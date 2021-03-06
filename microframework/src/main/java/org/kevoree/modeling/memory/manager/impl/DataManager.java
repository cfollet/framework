package org.kevoree.modeling.memory.manager.impl;

import org.kevoree.modeling.*;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.cdn.KContentDeliveryDriver;
import org.kevoree.modeling.cdn.KContentUpdateListener;
import org.kevoree.modeling.cdn.impl.MemoryContentDeliveryDriver;
import org.kevoree.modeling.memory.KChunk;
import org.kevoree.modeling.memory.KChunkFlags;
import org.kevoree.modeling.memory.space.KChunkIterator;
import org.kevoree.modeling.memory.space.KChunkSpaceManager;
import org.kevoree.modeling.memory.chunk.KObjectChunk;
import org.kevoree.modeling.memory.chunk.KLongLongMap;
import org.kevoree.modeling.memory.resolver.KResolver;
import org.kevoree.modeling.memory.resolver.impl.*;
import org.kevoree.modeling.memory.space.KChunkTypes;
import org.kevoree.modeling.memory.manager.internal.KInternalDataManager;
import org.kevoree.modeling.memory.space.KChunkSpace;
import org.kevoree.modeling.memory.manager.KDataManager;
import org.kevoree.modeling.message.KMessage;
import org.kevoree.modeling.message.impl.Message;
import org.kevoree.modeling.meta.KMetaClass;
import org.kevoree.modeling.meta.KMetaModel;
import org.kevoree.modeling.meta.impl.GenericObjectIndex;
import org.kevoree.modeling.scheduler.KScheduler;
import org.kevoree.modeling.operation.impl.HashOperationManager;
import org.kevoree.modeling.operation.KOperationManager;
import org.kevoree.modeling.scheduler.KTask;
import org.kevoree.modeling.util.Checker;
import org.kevoree.modeling.util.PrimitiveHelper;
import org.kevoree.modeling.util.maths.structure.blas.KBlas;

import java.util.concurrent.atomic.AtomicReference;

public class DataManager implements KDataManager, KInternalDataManager {

    private static final String UNIVERSE_NOT_CONNECTED_ERROR = "Please connect your createModel prior to create a universe or an object";

    private final KOperationManager _operationManager;
    private final KContentDeliveryDriver _db;
    private final KScheduler _scheduler;
    private final ListenerManager _listenerManager;
    private final KeyCalculator _modelKeyCalculator;
    private final KResolver _resolver;
    private final KChunkSpace _space;
    private final KChunkSpaceManager _spaceManager;
    private final KBlas _blas;

    private KeyCalculator _objectKeyCalculator = null;
    private KeyCalculator _universeKeyCalculator = null;
    private volatile boolean isConnected = false;

    private Short _prefix;
    private KModel _model;

    private static final int UNIVERSE_INDEX = 0;
    private static final int OBJ_INDEX = 1;
    private static final int GLO_TREE_INDEX = 2;
    private static final short zeroPrefix = 0;

    private int currentCdnListener = -1;

    @Override
    public void setModel(KModel p_model) {
        this._model = p_model;
    }

    public DataManager(KContentDeliveryDriver p_cdn, KScheduler p_scheduler, KChunkSpace p_space, KChunkSpaceManager p_spaceManager, KBlas p_blas) {
        this._space = p_space;
        this._space.setManager(this);
        this._spaceManager = p_spaceManager;
        this._spaceManager.setSpace(this._space);

        this._scheduler = p_scheduler;
        this._resolver = new DistortedTimeResolver(this._spaceManager, this);
        this._listenerManager = new ListenerManager();
        this._modelKeyCalculator = new KeyCalculator(zeroPrefix, 0);
        this._db = p_cdn;
        attachContentDeliveryDriver(new MemoryContentDeliveryDriver());
        this._operationManager = new HashOperationManager(this);
        this._blas = p_blas;
    }

    @Override
    public final KModel model() {
        return _model;
    }

    @Override
    public KBlas blas() {
        return this._blas;
    }

    /* Key Management Section */
    @Override
    public final long nextUniverseKey() {
        if (_universeKeyCalculator == null) {
            throw new RuntimeException(UNIVERSE_NOT_CONNECTED_ERROR);
        }
        return _universeKeyCalculator.nextKey();
    }

    @Override
    public final long nextObjectKey() {
        if (_objectKeyCalculator == null) {
            throw new RuntimeException(UNIVERSE_NOT_CONNECTED_ERROR);
        }
        return _objectKeyCalculator.nextKey();
    }

    @Override
    public final long nextModelKey() {
        return _modelKeyCalculator.nextKey();
    }

    @Override
    public final void initUniverse(long p_universe, long p_parent) {
        KLongLongMap cached = (KLongLongMap) _space.get(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG);
        if (cached != null && !cached.contains(p_universe)) {
            cached.put(p_universe, p_parent);
        }
    }

    private static final int PREFIX_TO_SAVE_SIZE = 2;
    private static final int KEY_SIZE = 3;

    @Override
    public void saveDirtyList(final KChunkIterator dirtyIterator, final KCallback<Throwable> callback) {
        //final DataManager selfPointer = this;
        //_scheduler.dispatch(new KTask() {
        //    @Override
        //    public void run() {

        // System.out.println("Save");

        if (dirtyIterator.size() == 0) {
            if (callback != null) {
                callback.on(null);
            }
            return;
        }
        int sizeToSaveKeys = (dirtyIterator.size() + PREFIX_TO_SAVE_SIZE) * KEY_SIZE;
        long[] toSaveKeys = new long[sizeToSaveKeys];
        int sizeToSaveValues = dirtyIterator.size() + PREFIX_TO_SAVE_SIZE;
        String[] toSaveValues = new String[sizeToSaveValues];
        int i = 0;
        KMetaModel _mm = this._model.metaModel();
        while (dirtyIterator.hasNext()) {
            KChunk loopChunk = dirtyIterator.next();
            if (loopChunk != null && (loopChunk.getFlags() & KChunkFlags.DIRTY_BIT) == KChunkFlags.DIRTY_BIT) {
                toSaveKeys[i * KEY_SIZE] = loopChunk.universe();
                toSaveKeys[i * KEY_SIZE + 1] = loopChunk.time();
                toSaveKeys[i * KEY_SIZE + 2] = loopChunk.obj();
                try {
                    toSaveValues[i] = loopChunk.serialize(_mm);
                    loopChunk.setFlags(0, KChunkFlags.DIRTY_BIT);
                    i++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        toSaveKeys[i * KEY_SIZE] = KConfig.BEGINNING_OF_TIME;
        toSaveKeys[i * KEY_SIZE + 1] = KConfig.NULL_LONG;
        toSaveKeys[i * KEY_SIZE + 2] = this._objectKeyCalculator.prefix();
        toSaveValues[i] = "" + this._objectKeyCalculator.lastComputedIndex();
        i++;
        toSaveKeys[i * KEY_SIZE] = KConfig.END_OF_TIME;
        toSaveKeys[i * KEY_SIZE + 1] = KConfig.NULL_LONG;
        toSaveKeys[i * KEY_SIZE + 2] = this._universeKeyCalculator.prefix();
        toSaveValues[i] = "" + this._universeKeyCalculator.lastComputedIndex();

        //shrink in case of i != full size
        if (i != sizeToSaveValues - 1) {
            //shrinkValue
            String[] toSaveValuesShrinked = new String[i + 1];
            System.arraycopy(toSaveValues, 0, toSaveValuesShrinked, 0, i + 1);
            toSaveValues = toSaveValuesShrinked;

            long[] toSaveKeysShrinked = new long[(i + 1) * KEY_SIZE];
            System.arraycopy(toSaveKeys, 0, toSaveKeysShrinked, 0, (i + 1) * KEY_SIZE);
            toSaveKeys = toSaveKeysShrinked;
        }

        this._db.put(toSaveKeys, toSaveValues, callback, this.currentCdnListener);
        // }
        // });
    }

    @Override
    public void save(final KCallback<Throwable> callback) {
        KChunkIterator dirtyIterator = this._space.detachDirties();
        saveDirtyList(dirtyIterator, callback);
    }

    @Override
    public void initKObject(KObject obj) {
        _resolver.indexObject(obj);
    }

    @Override
    public KObjectChunk preciseChunk(long universe, long time, long uuid, KMetaClass metaClass, AtomicReference<long[]> previousResolution) {
        KObjectChunk resolvedChunk = _resolver.preciseChunk(universe, time, uuid, metaClass, previousResolution);
        if (resolvedChunk != null) {
            return resolvedChunk;
        } else {
            //TODO
            throw new RuntimeException("Cache Miss, not implemented Yet " + universe + "," + time + "," + uuid);
        }
    }

    @Override
    public KObjectChunk closestChunk(long universe, long time, long uuid, KMetaClass metaClass, AtomicReference<long[]> previousResolution) {
        KObjectChunk resolvedChunk = _resolver.closestChunk(universe, time, uuid, metaClass, previousResolution);
        if (resolvedChunk != null) {
            return resolvedChunk;
        } else {
            long[] previous = previousResolution.get();
            throw new RuntimeException("Cache Miss / obj:" + universe + "," + time + "," + uuid + " / previous:" + previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX] + "," + previous[AbstractKObject.TIME_PREVIOUS_INDEX]);
        }
    }

    @Override
    public synchronized void connect(final KCallback<Throwable> connectCallback) {
        if (isConnected) {
            if (connectCallback != null) {
                connectCallback.on(null);
            }
        }
        if (_db == null) {
            if (connectCallback != null) {
                connectCallback.on(new Exception("Please attach a KDataBase AND a KBroker first !"));
            }
        } else {
            //connect the blas
            KBlas localBlas = _blas;
            if (localBlas != null) {
                localBlas.connect();
            }

            DataManager selfPointer = this;
            selfPointer._scheduler.start();
            selfPointer._scheduler.dispatch(new KTask() {
                @Override
                public void run() {
                    selfPointer._db.connect(new KCallback<Throwable>() {
                        @Override
                        public void on(Throwable throwable) {
                            if (throwable == null) {
                                String[] mappings = selfPointer._operationManager.mappings();
                                if (mappings != null && mappings.length >= 1) {
                                    KMessage operationMapping = new Message();
                                    operationMapping.setType(Message.OPERATION_MAPPING);
                                    operationMapping.setValues(mappings);
                                    selfPointer._db.sendToPeer(null, operationMapping, null);

                                }

                                selfPointer._db.atomicGetIncrement(new long[]{KConfig.END_OF_TIME, KConfig.NULL_LONG, KConfig.NULL_LONG},
                                        new KCallback<Short>() {
                                            @Override
                                            public void on(Short newPrefix) {
                                                selfPointer._prefix = newPrefix;
                                                long[] connectionKeys = new long[]{
                                                        KConfig.BEGINNING_OF_TIME, KConfig.NULL_LONG, newPrefix, //LastUniverseIndexFromPrefix
                                                        KConfig.END_OF_TIME, KConfig.NULL_LONG, newPrefix, //LastObjectIndexFromPrefix
                                                        KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG //GlobalUniverseTree
                                                };
                                                selfPointer._db.get(connectionKeys, new KCallback<String[]>() {
                                                    @Override
                                                    public void on(String[] strings) {
                                                        if (strings.length == 3) {
                                                            Exception detected = null;
                                                            try {
                                                                String uniIndexPayload = strings[UNIVERSE_INDEX];
                                                                if (uniIndexPayload == null || PrimitiveHelper.equals(uniIndexPayload, "")) {
                                                                    uniIndexPayload = "0";
                                                                }
                                                                String objIndexPayload = strings[OBJ_INDEX];
                                                                if (objIndexPayload == null || PrimitiveHelper.equals(objIndexPayload, "")) {
                                                                    objIndexPayload = "0";
                                                                }
                                                                String globalUniverseTreePayload = strings[GLO_TREE_INDEX];
                                                                KLongLongMap globalUniverseTree = (KLongLongMap) selfPointer._spaceManager.createAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, KChunkTypes.LONG_LONG_MAP);
                                                                if (globalUniverseTreePayload != null) {
                                                                    try {
                                                                        globalUniverseTree.init(globalUniverseTreePayload, selfPointer.model().metaModel(), -1);
                                                                    } catch (Exception e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                }
                                                                long newUniIndex = PrimitiveHelper.parseLong(uniIndexPayload);
                                                                long newObjIndex = PrimitiveHelper.parseLong(objIndexPayload);
                                                                selfPointer._universeKeyCalculator = new KeyCalculator(selfPointer._prefix, newUniIndex);
                                                                selfPointer._objectKeyCalculator = new KeyCalculator(selfPointer._prefix, newObjIndex);
                                                                selfPointer.isConnected = true;
                                                            } catch (Exception e) {
                                                                //e.printStackTrace();
                                                                detected = e;
                                                            }
                                                            if (connectCallback != null) {
                                                                connectCallback.on(detected);
                                                            }
                                                        } else {
                                                            if (connectCallback != null) {
                                                                connectCallback.on(new Exception("Error while connecting the KDataStore..."));
                                                            }
                                                        }

                                                    }
                                                });

                                            }
                                        });
                            } else {
                                if (connectCallback != null) {
                                    connectCallback.on(throwable);
                                }
                            }
                        }
                    });
                }
            });
        }
    }


    @Override
    public synchronized final void close(KCallback<Throwable> callback) {
        if (isConnected) {
            save(new KCallback<Throwable>() {
                @Override
                public void on(Throwable throwable) {
                    _scheduler.stop();
                    _blas.disconnect();
                    isConnected = false;
                    if (_db != null) {
                        _db.close(callback);
                    } else {
                        if (callback != null) {
                            callback.on(null);
                        }
                    }
                }
            });
        } else {
            if (callback != null) {
                callback.on(null);
            }
        }
    }

    @Override
    public void deleteUniverse(KUniverse p_universe, KCallback<Throwable> callback) {
        throw new RuntimeException("Not implemented yet !");
    }

    @Override
    public void lookup(long universe, long time, long uuid, KCallback<KObject> callback) {
        this._scheduler.dispatch(this._resolver.lookup(universe, time, uuid, callback));
    }

    @Override
    public void lookupAllObjects(long universe, long time, long[] uuids, KCallback<KObject[]> callback) {
        this._scheduler.dispatch(this._resolver.lookupAllObjects(universe, time, uuids, callback));
    }

    /**
     * @native ts
     * return null;
     */
    @Override
    public KObject[] syncLookupAllObjects(long universe, long time, long[] uuids) {
        //important!!!
        this._scheduler.detach();
        final KObject[][] result = new KObject[1][];
        java.util.concurrent.CountDownLatch counter = new java.util.concurrent.CountDownLatch(1);
        this._scheduler.dispatch(this._resolver.lookupAllObjects(universe, time, uuids, new KCallback<KObject[]>() {
            @Override
            public void on(KObject[] returnedResult) {
                result[0] = returnedResult;
                counter.countDown();
            }
        }));
        try {
            //TODO inform the scheduler of the blocking operation
            counter.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }

    @Override
    public void lookupAllTimes(long universe, long[] times, long uuid, KCallback<KObject[]> callback) {
        this._scheduler.dispatch(this._resolver.lookupAllTimes(universe, times, uuid, callback));
    }

    @Override
    public KPreparedLookup createPreparedLookup(int p_size) {
        return new PreparedLookup(p_size);
    }

    @Override
    public void lookupPrepared(KPreparedLookup prepared, KCallback<KObject[]> callback) {
        this._scheduler.dispatch(this._resolver.lookupPrepared(prepared, callback));
    }

    @Override
    public KContentDeliveryDriver cdn() {
        return this._db;
    }

    @Override
    public KScheduler scheduler() {
        return this._scheduler;
    }

    private void attachContentDeliveryDriver(KContentDeliveryDriver p_dataBase) {
        DataManager selfPointer = this;
        currentCdnListener = selfPointer._db.addUpdateListener(new KContentUpdateListener() {
            @Override
            public void onKeysUpdate(long[] updatedKeys) {
                long[] toLoadKeys = new long[updatedKeys.length];
                int toInsertNotifyKey = 0;
                long[] toNotifyKeys = new long[updatedKeys.length];
                int nbElements = updatedKeys.length / KEY_SIZE;
                int toInsertKey = 0;
                for (int i = 0; i < nbElements; i++) {
                    KChunk currentChunk = selfPointer._spaceManager.getAndMark(updatedKeys[i * 3], updatedKeys[i * 3 + 1], updatedKeys[i * 3 + 2]);
                    //reload object if necessary
                    if (currentChunk != null) {
                        if ((currentChunk.getFlags() & KChunkFlags.DIRTY_BIT) != KChunkFlags.DIRTY_BIT) {
                            toLoadKeys[toInsertKey * KEY_SIZE] = updatedKeys[i * KEY_SIZE];
                            toLoadKeys[toInsertKey * KEY_SIZE + 1] = updatedKeys[i * KEY_SIZE + 1];
                            toLoadKeys[toInsertKey * KEY_SIZE + 2] = updatedKeys[i * KEY_SIZE + 2];
                            toInsertKey++;
                        }
                        selfPointer._spaceManager.unmarkMemoryElement(currentChunk);
                    }
                    //check if this is an object chunk
                    if (selfPointer._listenerManager.isListened(updatedKeys[i * KEY_SIZE + 2]) && updatedKeys[i * KEY_SIZE] != KConfig.NULL_LONG && updatedKeys[i * KEY_SIZE + 1] != KConfig.NULL_LONG && updatedKeys[i * KEY_SIZE + 2] != KConfig.NULL_LONG) {
                        //check if the object is listened anyway
                        toNotifyKeys[toInsertNotifyKey * KEY_SIZE] = updatedKeys[i * KEY_SIZE];
                        toNotifyKeys[toInsertNotifyKey * KEY_SIZE + 1] = updatedKeys[i * KEY_SIZE + 1];
                        toNotifyKeys[toInsertNotifyKey * KEY_SIZE + 2] = updatedKeys[i * KEY_SIZE + 2];
                        toInsertNotifyKey++;
                    }
                }
                if (toInsertKey == 0 && toInsertNotifyKey == 0) {
                    return;
                }
                final long[] trimmedToLoad = new long[toInsertKey * 3];
                System.arraycopy(toLoadKeys, 0, trimmedToLoad, 0, toInsertKey * 3);
                final long[] trimmedToNotify = new long[toInsertNotifyKey * 3];
                System.arraycopy(toNotifyKeys, 0, trimmedToNotify, 0, toInsertNotifyKey * 3);

                KMetaModel mm = selfPointer._model.metaModel();
                selfPointer._db.get(trimmedToLoad, new KCallback<String[]>() {
                    @Override
                    public void on(String[] payloads) {
                        for (int i = 0; i < payloads.length; i++) {
                            if (payloads[i] != null) {
                                KChunk currentChunk = selfPointer._spaceManager.getAndMark(trimmedToLoad[i * 3], trimmedToLoad[i * 3 + 1], trimmedToLoad[i * 3 + 2]);
                                if (currentChunk != null) {
                                    currentChunk.init(payloads[i], mm, -1);
                                    selfPointer._spaceManager.unmarkMemoryElement(currentChunk);
                                }
                            }
                        }
                        //now call a lookup on all elements that have to be notify
                        selfPointer._resolver.lookupPreciseKeys(trimmedToNotify, new KCallback<KObject[]>() {
                            @Override
                            public void on(KObject[] updatedObjects) {
                                selfPointer._listenerManager.dispatch(updatedObjects);
                            }
                        }).run();
                    }
                });
            }

            @Override
            public void onOperationCall(KMessage operationCallMessage) {
                selfPointer._operationManager.dispatch(operationCallMessage);
            }
        });
    }

    public KOperationManager operationManager() {
        return _operationManager;
    }

    @Override
    public KListener createListener(long p_universe) {
        return this._listenerManager.createListener(p_universe);
    }


    @Override
    public void resolveTimes(long currentUniverse, long currentUuid, long startTime, long endTime, KCallback<long[]> callback) {
        _resolver.resolveTimes(currentUniverse, currentUuid, startTime, endTime, callback);
    }

    @Override
    public int spaceSize() {
        return _space.size();
    }

    @Override
    public void printDebug() {
        this._space.printDebug(_model.metaModel());
    }

    @Override
    public void destroyObject(KObject victim) {
        AbstractKObject castedVictim = (AbstractKObject) victim;
        long[] previous;
        do {
            previous = castedVictim.previousResolved().get();
        } while (!castedVictim.previousResolved().compareAndSet(previous, null));
        if (previous != null) {
            this._spaceManager.unmark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], previous[AbstractKObject.TIME_PREVIOUS_INDEX], victim.uuid());//FREE OBJECT CHUNK
            this._spaceManager.unmark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], KConfig.NULL_LONG, victim.uuid());//FREE TIME TREE
            this._spaceManager.unmark(KConfig.NULL_LONG, KConfig.NULL_LONG, victim.uuid()); //FREE OBJECT UNIVERSE MAP
            this._spaceManager.unmark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG); //FREE GLOBAL UNIVERSE MAP
        }
    }

    @Override
    public KChunkSpace space() {
        return this._space;
    }

    @Override
    public void index(long universe, long time, String indexName, boolean createIfAbsent, KCallback<KObjectIndex> callback) {
        DataManager selfPointer = this;
        selfPointer._scheduler.dispatch(selfPointer._resolver.lookup(universe, time, KConfig.END_OF_TIME, new KCallback<KObject>() {
            @Override
            public void on(KObject kObject) {
                KObjectIndex globalIndex = (KObjectIndex) kObject;
                if (globalIndex == null && createIfAbsent) {
                    globalIndex = new GenericObjectIndex(universe, time, KConfig.END_OF_TIME, selfPointer, universe, time, KConfig.NULL_LONG, KConfig.NULL_LONG);
                    initKObject(globalIndex);
                }
                if (globalIndex == null) {
                    if (Checker.isDefined(callback)) {
                        callback.on(null);
                    }
                } else {
                    long indexUUID = globalIndex.getIndex(indexName);
                    if (indexUUID == KConfig.NULL_LONG && createIfAbsent) {
                        long nextKey = nextObjectKey();
                        KObjectIndex namedIndex = new GenericObjectIndex(universe, time, nextKey, selfPointer, universe, time, KConfig.NULL_LONG, KConfig.NULL_LONG);
                        initKObject(namedIndex);
                        globalIndex.setIndex(indexName, nextKey);
                        if (Checker.isDefined(callback)) {
                            callback.on(namedIndex);
                        }
                    } else {
                        if (indexUUID == KConfig.NULL_LONG) {
                            if (Checker.isDefined(callback)) {
                                callback.on(null);
                            }
                        } else {
                            selfPointer._scheduler.dispatch(selfPointer._resolver.lookup(universe, time, indexUUID, new KCallback<KObject>() {
                                @Override
                                public void on(KObject namedIndex) {
                                    if (Checker.isDefined(callback)) {
                                        callback.on((KObjectIndex) namedIndex);
                                    }
                                }
                            }));
                        }
                    }
                }
            }
        }));
    }

}
