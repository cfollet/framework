package org.kevoree.modeling.memory.resolver.impl;

import org.kevoree.modeling.*;
import org.kevoree.modeling.abs.AbstractKModel;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.memory.KChunk;
import org.kevoree.modeling.memory.KChunkFlags;
import org.kevoree.modeling.memory.chunk.*;
import org.kevoree.modeling.memory.space.KChunkSpaceManager;
import org.kevoree.modeling.memory.manager.internal.KInternalDataManager;
import org.kevoree.modeling.memory.chunk.impl.ArrayLongLongMap;
import org.kevoree.modeling.memory.resolver.KResolver;
import org.kevoree.modeling.memory.space.KChunkTypes;
import org.kevoree.modeling.meta.KMetaClass;
import org.kevoree.modeling.meta.impl.GenericObjectIndex;
import org.kevoree.modeling.meta.impl.MetaClassIndex;
import org.kevoree.modeling.scheduler.KTask;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class DistortedTimeResolver implements KResolver {

    private static final int KEYS_SIZE = 3;

    private final KChunkSpaceManager _spaceManager;

    private final KInternalDataManager _manager;

    private Random random;

    public DistortedTimeResolver(KChunkSpaceManager p_cache, KInternalDataManager p_manager) {
        this._spaceManager = p_cache;
        this._manager = p_manager;
        this.random = new Random();
    }

    @Override
    public final KTask lookup(final long universe, final long time, final long uuid, final KCallback<KObject> callback) {
        final DistortedTimeResolver selfPointer = this;
        return new KTask() {
            @Override
            public void run() {
                try {
                    selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, new KCallback<KChunk>() {
                        @Override
                        public void on(KChunk theGlobalUniverseOrderElement) {
                            if (theGlobalUniverseOrderElement != null) {
                                selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, uuid, new KCallback<KChunk>() {
                                    @Override
                                    public void on(KChunk theObjectUniverseOrderElement) {
                                        if (theObjectUniverseOrderElement == null) {
                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                            callback.on(null);
                                        } else {
                                            long closestUniverse = resolve_universe((KLongLongMap) theGlobalUniverseOrderElement, (KLongLongMap) theObjectUniverseOrderElement, time, universe);
                                            selfPointer.getOrLoadAndMark(closestUniverse, KConfig.NULL_LONG, uuid, new KCallback<KChunk>() {
                                                @Override
                                                public void on(KChunk theObjectTimeTreeElement) {
                                                    if (theObjectTimeTreeElement == null) {
                                                        selfPointer._spaceManager.unmarkMemoryElement(theObjectUniverseOrderElement);
                                                        selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                        callback.on(null);
                                                    } else {
                                                        long closestTime = ((KLongTree) theObjectTimeTreeElement).previousOrEqual(time);
                                                        if (closestTime == KConfig.NULL_LONG) {
                                                            selfPointer._spaceManager.unmarkMemoryElement(theObjectTimeTreeElement);
                                                            selfPointer._spaceManager.unmarkMemoryElement(theObjectUniverseOrderElement);
                                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                            callback.on(null);
                                                            return;
                                                        }
                                                        selfPointer.getOrLoadAndMark(closestUniverse, closestTime, uuid, new KCallback<KChunk>() {
                                                            @Override
                                                            public void on(KChunk theObjectChunk) {
                                                                if (theObjectChunk == null) {
                                                                    selfPointer._spaceManager.unmarkMemoryElement(theObjectTimeTreeElement);
                                                                    selfPointer._spaceManager.unmarkMemoryElement(theObjectUniverseOrderElement);
                                                                    selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                                    callback.on(null);
                                                                } else {
                                                                    int metaClassIndex = ((KObjectChunk) theObjectChunk).metaClassIndex();
                                                                    KObject newProxy;
                                                                    if (metaClassIndex == MetaClassIndex.INSTANCE.index()) {
                                                                        newProxy = new GenericObjectIndex(universe, time, uuid, selfPointer._manager, closestUniverse, closestTime, ((KLongLongMap) theObjectUniverseOrderElement).magic(), ((KLongTree) theObjectTimeTreeElement).magic());
                                                                    } else {
                                                                        KMetaClass resolvedMetaClass = selfPointer._manager.model().metaModel().metaClass(((KObjectChunk) theObjectChunk).metaClassIndex());
                                                                        if (resolvedMetaClass == null) {
                                                                            throw new RuntimeException("NullResolvedClass");
                                                                        }
                                                                        newProxy = ((AbstractKModel) selfPointer._manager.model()).createProxy(universe, time, uuid, resolvedMetaClass, closestUniverse, closestTime, ((KLongLongMap) theObjectUniverseOrderElement).magic(), ((KLongTree) theObjectTimeTreeElement).magic());
                                                                    }
                                                                    selfPointer._spaceManager.register(newProxy);
                                                                    callback.on(newProxy);
                                                                }
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                                        }
                                    }
                                });
                            } else {
                                callback.on(null);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public final KTask lookupAllObjects(long universe, long time, long[] uuids, KCallback<KObject[]> callback) {
        final DistortedTimeResolver selfPointer = this;
        return new KTask() {
            @Override
            public void run() {
                try {
                    selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, new KCallback<KChunk>() {
                        @Override
                        public void on(KChunk theGlobalUniverseOrderElement) {
                            if (theGlobalUniverseOrderElement != null) {
                                final long[] tempObjectUniverseKeys = new long[uuids.length * 3];
                                for (int i = 0; i < uuids.length; i++) {
                                    tempObjectUniverseKeys[i * 3] = KConfig.NULL_LONG;
                                    tempObjectUniverseKeys[i * 3 + 1] = KConfig.NULL_LONG;
                                    tempObjectUniverseKeys[i * 3 + 2] = uuids[i];
                                }
                                selfPointer.getOrLoadAndMarkAll(tempObjectUniverseKeys, new KCallback<KChunk[]>() {
                                    @Override
                                    public void on(KChunk[] objectUniverseOrderElements) {
                                        if (objectUniverseOrderElements == null || objectUniverseOrderElements.length == 0) {
                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                            callback.on(new KObject[0]);
                                            return;
                                        }
                                        final long[] tempObjectTimeTreeKeys = new long[uuids.length * 3];
                                        for (int i = 0; i < uuids.length; i++) {
                                            long closestUniverse = resolve_universe((KLongLongMap) theGlobalUniverseOrderElement, (KLongLongMap) objectUniverseOrderElements[i], time, universe);
                                            tempObjectTimeTreeKeys[i * 3] = closestUniverse;
                                            tempObjectTimeTreeKeys[i * 3 + 1] = KConfig.NULL_LONG;
                                            tempObjectTimeTreeKeys[i * 3 + 2] = uuids[i];
                                        }
                                        selfPointer.getOrLoadAndMarkAll(tempObjectTimeTreeKeys, new KCallback<KChunk[]>() {
                                            @Override
                                            public void on(KChunk[] objectTimeTreeElements) {
                                                if (objectTimeTreeElements == null || objectTimeTreeElements.length == 0) {
                                                    selfPointer._spaceManager.unmarkAllMemoryElements(objectUniverseOrderElements);
                                                    selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                    callback.on(new KObject[0]);
                                                    return;
                                                }
                                                final long[] tempObjectChunkKeys = new long[uuids.length * 3];
                                                for (int i = 0; i < uuids.length; i++) {
                                                    long closestTime = ((KLongTree) objectTimeTreeElements[i]).previousOrEqual(time);
                                                    if (closestTime != KConfig.NULL_LONG) {
                                                        tempObjectChunkKeys[i * 3] = tempObjectTimeTreeKeys[i * 3];
                                                        tempObjectChunkKeys[i * 3 + 1] = closestTime;
                                                        tempObjectChunkKeys[i * 3 + 2] = uuids[i];
                                                    } else {
                                                        System.arraycopy(KContentKey.NULL_KEY, 0, tempObjectChunkKeys, (i * 3), 3);
                                                    }
                                                }
                                                selfPointer.getOrLoadAndMarkAll(tempObjectChunkKeys, new KCallback<KChunk[]>() {
                                                    @Override
                                                    public void on(KChunk[] theObjectChunks) {
                                                        if (theObjectChunks == null || theObjectChunks.length == 0) {
                                                            selfPointer._spaceManager.unmarkAllMemoryElements(objectTimeTreeElements);
                                                            selfPointer._spaceManager.unmarkAllMemoryElements(objectUniverseOrderElements);
                                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                            callback.on(new KObject[0]);
                                                        } else {
                                                            KObject[] finalResult = new KObject[uuids.length];
                                                            for (int h = 0; h < theObjectChunks.length; h++) {
                                                                if (theObjectChunks[h] != null) {
                                                                    finalResult[h] = ((AbstractKModel) selfPointer._manager.model()).createProxy(universe, time, uuids[h], selfPointer._manager.model().metaModel().metaClass(((KObjectChunk) theObjectChunks[h]).metaClassIndex()), tempObjectTimeTreeKeys[h * 3], tempObjectChunkKeys[h * 3 + 1], ((KLongLongMap) objectUniverseOrderElements[h]).magic(), ((KLongTree) objectTimeTreeElements[h]).magic());
                                                                } else {
                                                                    finalResult[h] = null;
                                                                }
                                                            }
                                                            selfPointer._spaceManager.registerAll(finalResult);
                                                            callback.on(finalResult);
                                                        }
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public final KTask lookupPreciseKeys(long[] keys, KCallback<KObject[]> callback) {
        final DistortedTimeResolver selfPointer = this;
        return new KTask() {
            @Override
            public void run() {
                try {
                    selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, new KCallback<KChunk>() {
                        @Override
                        public void on(KChunk theGlobalUniverseOrderElement) {
                            if (theGlobalUniverseOrderElement != null) {
                                final long[] allOrderedKeys = new long[keys.length * 3];
                                int insertIndex = 0;
                                int nbKeys = keys.length / 3;
                                for (int i = 0; i < nbKeys; i++) {
                                    //objectUniverseOrder
                                    allOrderedKeys[insertIndex] = KConfig.NULL_LONG;
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = KConfig.NULL_LONG;
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = keys[i * 3 + 2];
                                    insertIndex++;
                                    //objectTimeORder
                                    allOrderedKeys[insertIndex] = keys[i * 3];
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = KConfig.NULL_LONG;
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = keys[i * 3 + 2];
                                    insertIndex++;
                                    //objectChunk
                                    allOrderedKeys[insertIndex] = keys[i * 3];
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = keys[i * 3 + 1];
                                    insertIndex++;
                                    allOrderedKeys[insertIndex] = keys[i * 3 + 2];
                                    insertIndex++;
                                }
                                selfPointer.getOrLoadAndMarkAll(allOrderedKeys, new KCallback<KChunk[]>() {
                                    @Override
                                    public void on(KChunk[] kChunks) {
                                        if (kChunks == null || kChunks.length == 0) {
                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                            callback.on(new KObject[0]);
                                        } else {
                                            KObject[] finalResult = new KObject[nbKeys];
                                            int insertIndex = 0;
                                            int previousClassIndex = -1;
                                            for (int h = 0; h < kChunks.length; h++) {
                                                if (kChunks[h] != null && kChunks[h].type() == KChunkTypes.OBJECT_CHUNK) {
                                                    finalResult[insertIndex] = ((AbstractKModel) selfPointer._manager.model()).createProxy(kChunks[h].universe(), kChunks[h].time(), kChunks[h].obj(), selfPointer._manager.model().metaModel().metaClass(previousClassIndex), kChunks[h].universe(), kChunks[h].time(), KConfig.NULL_LONG, KConfig.NULL_LONG);
                                                    insertIndex++;
                                                } else if (kChunks[h] != null && kChunks[h].type() == KChunkTypes.LONG_LONG_MAP) {
                                                    KLongLongMap casted = (KLongLongMap) kChunks[h];
                                                    previousClassIndex = casted.metaClassIndex();
                                                }
                                            }
                                            selfPointer._spaceManager.registerAll(finalResult);
                                            callback.on(finalResult);
                                        }
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public KTask lookupPrepared(final KPreparedLookup preparedLookup, final KCallback<KObject[]> callback) {
        final DistortedTimeResolver selfPointer = this;
        final int nbObjs = preparedLookup.flatLookup().length / 3;
        final long[] flat = preparedLookup.flatLookup();
        return new KTask() {
            @Override
            public void run() {
                try {
                    selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, new KCallback<KChunk>() {
                        @Override
                        public void on(KChunk theGlobalUniverseOrderElement) {
                            if (theGlobalUniverseOrderElement != null) {
                                final long[] tempObjectUniverseKeys = new long[nbObjs * 3];
                                for (int i = 0; i < nbObjs; i++) {
                                    tempObjectUniverseKeys[i * 3] = KConfig.NULL_LONG;
                                    tempObjectUniverseKeys[i * 3 + 1] = KConfig.NULL_LONG;
                                    tempObjectUniverseKeys[i * 3 + 2] = flat[i * 3 + 2];
                                }
                                selfPointer.getOrLoadAndMarkAll(tempObjectUniverseKeys, new KCallback<KChunk[]>() {
                                    @Override
                                    public void on(KChunk[] objectUniverseOrderElements) {
                                        if (objectUniverseOrderElements == null || objectUniverseOrderElements.length == 0) {
                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                            callback.on(new KObject[0]);
                                            return;
                                        }
                                        final long[] tempObjectTimeTreeKeys = new long[nbObjs * 3];
                                        for (int i = 0; i < nbObjs; i++) {
                                            long closestUniverse = resolve_universe((KLongLongMap) theGlobalUniverseOrderElement, (KLongLongMap) objectUniverseOrderElements[i], flat[i * 3 + 1], flat[i * 3]);
                                            tempObjectTimeTreeKeys[i * 3] = closestUniverse;
                                            tempObjectTimeTreeKeys[i * 3 + 1] = KConfig.NULL_LONG;
                                            tempObjectTimeTreeKeys[i * 3 + 2] = flat[i * 3 + 2];
                                        }
                                        selfPointer.getOrLoadAndMarkAll(tempObjectTimeTreeKeys, new KCallback<KChunk[]>() {
                                            @Override
                                            public void on(KChunk[] objectTimeTreeElements) {
                                                if (objectTimeTreeElements == null || objectTimeTreeElements.length == 0) {
                                                    selfPointer._spaceManager.unmarkAllMemoryElements(objectUniverseOrderElements);
                                                    selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                    callback.on(new KObject[0]);
                                                    return;
                                                }
                                                final long[] tempObjectChunkKeys = new long[nbObjs * 3];
                                                for (int i = 0; i < nbObjs; i++) {
                                                    long closestTime = ((KLongTree) objectTimeTreeElements[i]).previousOrEqual(flat[i * 3 + 1]);
                                                    if (closestTime != KConfig.NULL_LONG) {
                                                        tempObjectChunkKeys[i * 3] = tempObjectTimeTreeKeys[i * 3];
                                                        tempObjectChunkKeys[i * 3 + 1] = closestTime;
                                                        tempObjectChunkKeys[i * 3 + 2] = flat[i * 3 + 2];
                                                    } else {
                                                        System.arraycopy(KContentKey.NULL_KEY, 0, tempObjectChunkKeys, (i * 3), 3);
                                                    }
                                                }
                                                selfPointer.getOrLoadAndMarkAll(tempObjectChunkKeys, new KCallback<KChunk[]>() {
                                                    @Override
                                                    public void on(KChunk[] theObjectChunks) {
                                                        if (theObjectChunks == null || theObjectChunks.length == 0) {
                                                            selfPointer._spaceManager.unmarkAllMemoryElements(objectTimeTreeElements);
                                                            selfPointer._spaceManager.unmarkAllMemoryElements(objectUniverseOrderElements);
                                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                            callback.on(new KObject[0]);
                                                        } else {
                                                            KObject[] finalResult = new KObject[nbObjs];
                                                            for (int h = 0; h < theObjectChunks.length; h++) {
                                                                if (theObjectChunks[h] != null) {
                                                                    finalResult[h] = ((AbstractKModel) selfPointer._manager.model()).createProxy(flat[h * 3], flat[h * 3 + 1], flat[h * 3 + 2], selfPointer._manager.model().metaModel().metaClass(((KObjectChunk) theObjectChunks[h]).metaClassIndex()), tempObjectTimeTreeKeys[h * 3], tempObjectChunkKeys[h * 3 + 1], ((KLongLongMap) objectUniverseOrderElements[h]).magic(), ((KLongTree) objectTimeTreeElements[h]).magic());
                                                                } else {
                                                                    finalResult[h] = null;
                                                                }
                                                            }
                                                            selfPointer._spaceManager.registerAll(finalResult);
                                                            callback.on(finalResult);
                                                        }
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public final KTask lookupAllTimes(long universe, long[] times, long uuid, KCallback<KObject[]> callback) {
        final DistortedTimeResolver selfPointer = this;
        return new KTask() {
            @Override
            public void run() {
                try {
                    selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG, new KCallback<KChunk>() {
                        @Override
                        public void on(KChunk theGlobalUniverseOrderElement) {
                            if (theGlobalUniverseOrderElement != null) {
                                selfPointer.getOrLoadAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, uuid, new KCallback<KChunk>() {
                                    @Override
                                    public void on(KChunk theObjectUniverseOrderElement) {
                                        if (theObjectUniverseOrderElement == null) {
                                            selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                            callback.on(null);
                                        } else {
                                            final long[] closestUniverses = new long[times.length];
                                            //   final long[] closestUniverseMagics = new long[times.length];
                                            ArrayLongLongMap closestUnikUniverse = new ArrayLongLongMap(-1, -1, -1, null);
                                            int nbUniverseToload = 0;
                                            for (int i = 0; i < times.length; i++) {
                                                closestUniverses[i] = resolve_universe((KLongLongMap) theGlobalUniverseOrderElement, (KLongLongMap) theObjectUniverseOrderElement, times[i], universe);
                                                //  closestUniverseMagics[i] = ((KLongLongMap) theObjectUniverseOrderElement).magic();
                                                if (!closestUnikUniverse.contains(closestUniverses[i])) {
                                                    closestUnikUniverse.put(closestUniverses[i], nbUniverseToload);
                                                    nbUniverseToload++;
                                                }
                                            }
                                            long[] toLoadUniverseKeys = new long[nbUniverseToload * 3];
                                            closestUnikUniverse.each(new KLongLongMapCallBack() {
                                                @Override
                                                public void on(long key, long value) {
                                                    int currentIndex = (int) (value * 3);
                                                    toLoadUniverseKeys[currentIndex] = value;
                                                    toLoadUniverseKeys[currentIndex + 1] = KConfig.NULL_LONG;
                                                    toLoadUniverseKeys[currentIndex + 2] = uuid;
                                                }
                                            });
                                            selfPointer.getOrLoadAndMarkAll(toLoadUniverseKeys, new KCallback<KChunk[]>() {
                                                @Override
                                                public void on(KChunk[] objectTimeTreeElements) {
                                                    if (objectTimeTreeElements == null || objectTimeTreeElements.length == 0) {
                                                        selfPointer._spaceManager.unmarkMemoryElement(theObjectUniverseOrderElement);
                                                        selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                        callback.on(null);
                                                    } else {
                                                        final long[] closestTimes = new long[times.length];
                                                        final long[] closestTimesMagic = new long[times.length];
                                                        final ArrayLongLongMap closestUnikTimes = new ArrayLongLongMap(-1, -1, -1, null);
                                                        ArrayLongLongMap reverseTimeUniverse = new ArrayLongLongMap(-1, -1, -1, null);
                                                        int nbTimesToload = 0;
                                                        for (int i = 0; i < times.length; i++) {
                                                            int alignedIndexOfUniverse = (int) closestUnikUniverse.get(closestUniverses[i]);
                                                            closestTimes[i] = ((KLongTree) objectTimeTreeElements[alignedIndexOfUniverse]).previousOrEqual(times[i]);
                                                            closestTimesMagic[i] = ((KLongTree) objectTimeTreeElements[alignedIndexOfUniverse]).magic();
                                                            if (!closestUnikTimes.contains(closestTimes[i])) {
                                                                closestUnikTimes.put(closestTimes[i], nbTimesToload);
                                                                reverseTimeUniverse.put(closestTimes[i], closestUniverses[i]);
                                                                nbTimesToload++;
                                                            }
                                                        }
                                                        long[] toLoadTimesKeys = new long[nbTimesToload * 3];
                                                        closestUnikTimes.each(new KLongLongMapCallBack() {
                                                            @Override
                                                            public void on(long key, long value) {
                                                                int currentIndex = (int) (value * 3);
                                                                toLoadTimesKeys[currentIndex] = reverseTimeUniverse.get(key);
                                                                toLoadTimesKeys[currentIndex + 1] = key;
                                                                toLoadTimesKeys[currentIndex + 2] = uuid;
                                                            }
                                                        });
                                                        selfPointer.getOrLoadAndMarkAll(toLoadTimesKeys, new KCallback<KChunk[]>() {
                                                            @Override
                                                            public void on(KChunk[] objectChunks) {
                                                                if (objectChunks == null || objectChunks.length == 0) {
                                                                    selfPointer._spaceManager.unmarkAllMemoryElements(objectTimeTreeElements);
                                                                    selfPointer._spaceManager.unmarkMemoryElement(theObjectUniverseOrderElement);
                                                                    selfPointer._spaceManager.unmarkMemoryElement(theGlobalUniverseOrderElement);
                                                                    callback.on(null);
                                                                } else {
                                                                    KObject[] result = new KObject[times.length];
                                                                    for (int i = 0; i < times.length; i++) {
                                                                        long resolvedUniverse = closestUniverses[i];
                                                                        long resolvedTime = closestTimes[i];
                                                                        long timeMagic = closestTimesMagic[i];
                                                                        // long resolvedUniverseMagic = closestUniverseMagics[i];
                                                                        int indexChunks = (int) closestUnikTimes.get(closestTimes[i]);
                                                                        if (indexChunks != -1 && resolvedUniverse != KConfig.NULL_LONG && resolvedTime != KConfig.NULL_LONG) {
                                                                            result[i] = ((AbstractKModel) selfPointer._manager.model()).createProxy(universe, times[i], uuid, selfPointer._manager.model().metaModel().metaClass(((KObjectChunk) objectChunks[indexChunks]).metaClassIndex()), resolvedUniverse, resolvedTime, ((KLongLongMap) theObjectUniverseOrderElement).magic(), timeMagic);
                                                                        } else {
                                                                            result[i] = null;
                                                                        }
                                                                    }
                                                                    selfPointer._spaceManager.registerAll(result);
                                                                    callback.on(result);
                                                                }
                                                            }
                                                        });
                                                    }
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public KObjectChunk preciseChunk(long universe, long time, long uuid, KMetaClass metaClass, AtomicReference<long[]> previousResolution) {
        return internal_chunk(universe, time, uuid, false, metaClass, previousResolution);
    }

    @Override
    public KObjectChunk closestChunk(long universe, long time, long uuid, KMetaClass metaClass, AtomicReference<long[]> previousResolution) {
        return internal_chunk(universe, time, uuid, true, metaClass, previousResolution);
    }

    private KObjectChunk internal_chunk(long universe, long requestedTime, long uuid, boolean useClosest, KMetaClass metaClass, AtomicReference<long[]> previousResolution) {
        //protection against deleted KObjects
        if (previousResolution.get() == null) {
            throw new RuntimeException("This KObject has been tagged destroyed, please don't use it anymore!");
        }
        //time alignment according to KObject meta parameter
        long time = requestedTime;
        if (metaClass.temporalResolution() != 1) {
            time = time - (time % metaClass.temporalResolution());
        }
        //let's go for the resolution now
        long[] previous = previousResolution.get();
        if (previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX] == universe && previous[AbstractKObject.TIME_PREVIOUS_INDEX] == time) {
            //OPTIMIZATION #1: DEPHASING
            KObjectChunk currentEntry = (KObjectChunk) _spaceManager.getAndMark(universe, time, uuid);
            if (currentEntry != null) {
                _spaceManager.unmarkMemoryElement(currentEntry);
                return currentEntry;
            }
        }

        KLongLongMap objectUniverseMap = (KLongLongMap) _spaceManager.getAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, uuid);
        if (objectUniverseMap == null) {
            return null;
        }
        KLongTree objectTimeTree = (KLongTree) _spaceManager.getAndMark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], KConfig.NULL_LONG, uuid);
        if (objectTimeTree == null) {
            _spaceManager.unmarkMemoryElement(objectUniverseMap);
            return null;
        }
        long objectUniverseMapMagic = objectUniverseMap.magic();
        long objectTimeTreeMagic = objectTimeTree.magic();


        if (useClosest && previous[AbstractKObject.UNIVERSE_PREVIOUS_MAGIC] == objectUniverseMapMagic && previous[AbstractKObject.TIME_PREVIOUS_MAGIC] == objectTimeTreeMagic) {
            //OPTIMIZATION #2: SAME DEPHASING
            KObjectChunk currentEntry = (KObjectChunk) _spaceManager.getAndMark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], previous[AbstractKObject.TIME_PREVIOUS_INDEX], uuid);
            _spaceManager.unmarkMemoryElement(objectUniverseMap);
            _spaceManager.unmarkMemoryElement(objectTimeTree);
            if (currentEntry != null) {
                //ERROR case protection, chunk has been removed from cache
                _spaceManager.unmarkMemoryElement(currentEntry);
            }
            return currentEntry;
        }

        //NOMINAL CASE, MAGIC NUMBER ARE NOT VALID ANYMORE
        KLongLongMap globalUniverseTree = (KLongLongMap) _spaceManager.getAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG);
        if (globalUniverseTree == null) {
            _spaceManager.unmarkMemoryElement(objectUniverseMap);
            _spaceManager.unmarkMemoryElement(objectTimeTree);
            return null;
        }

        //SOMETHING WILL MOVE HERE ANYWAY SO WE SYNC THE OBJECT
        int magic;
        do {
            magic = random.nextInt();
        } while (!objectUniverseMap.tokenCompareAndSwap(-1, magic));

        //OK NOW WE HAVE THE MAGIC FOR UUID

        try {
            long resolvedUniverse = resolve_universe(globalUniverseTree, objectUniverseMap, time, universe);
            long resolvedTime = objectTimeTree.previousOrEqual(time);
            if (resolvedUniverse != KConfig.NULL_LONG && resolvedTime != KConfig.NULL_LONG) {
                if (useClosest) {
                    KObjectChunk newObjectEntry = (KObjectChunk) _spaceManager.getAndMark(resolvedUniverse, resolvedTime, uuid);
                    long[] current;
                    boolean diff = false;
                    do {
                        previous = previousResolution.get();
                        if (previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX] != resolvedUniverse || previous[AbstractKObject.TIME_PREVIOUS_INDEX] != resolvedTime) {
                            current = new long[]{resolvedUniverse, resolvedTime, objectUniverseMapMagic, objectTimeTreeMagic};
                            diff = true;
                        } else {
                            current = previous;
                        }
                    } while (!previousResolution.compareAndSet(previous, current));
                    if (diff) {
                        //unmark previous
                        _spaceManager.unmark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], previous[AbstractKObject.TIME_PREVIOUS_INDEX], uuid);
                    } else {
                        _spaceManager.unmarkMemoryElement(newObjectEntry);
                    }
                    //in all the case free tree and map
                    _spaceManager.unmarkMemoryElement(objectTimeTree);
                    _spaceManager.unmarkMemoryElement(globalUniverseTree);
                    _spaceManager.unmarkMemoryElement(objectUniverseMap);
                    //free lock
                    if (!objectUniverseMap.tokenCompareAndSwap(magic, -1)) {
                        throw new RuntimeException("BadCompareAndSwap");
                    }
                    return newObjectEntry;
                } else {
                    long[] current;
                    boolean diff = false;
                    do {
                        previous = previousResolution.get();
                        if (previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX] != universe || previous[AbstractKObject.TIME_PREVIOUS_INDEX] != time) {
                            //universeMap and objectTree magic numbers are set to null
                            current = new long[]{universe, time, KConfig.NULL_LONG, KConfig.NULL_LONG};
                            diff = true;
                        } else {
                            current = previous;
                        }
                    } while (!previousResolution.compareAndSet(previous, current));
                    if (diff) {
                        KObjectChunk currentEntry = (KObjectChunk) _spaceManager.getAndMark(previous[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], previous[AbstractKObject.TIME_PREVIOUS_INDEX], uuid);
                        KObjectChunk clonedChunk = _spaceManager.cloneAndMark(currentEntry, universe, time, uuid, _manager.model().metaModel());
                        if (resolvedUniverse == universe) {
                            objectTimeTree.insertKey(time);
                        } else {
                            KLongTree newTemporalTree = (KLongTree) _spaceManager.createAndMark(universe, KConfig.NULL_LONG, uuid, KChunkTypes.LONG_TREE);
                            newTemporalTree.insertKey(time);
                            _spaceManager.unmarkMemoryElement(objectTimeTree);
                            objectUniverseMap.put(universe, time);
                        }
                        //double unMarking, because, we should not use anymore this object
                        _spaceManager.unmarkMemoryElement(currentEntry);
                        _spaceManager.unmarkMemoryElement(currentEntry);
                        //free the rest of used object
                        _spaceManager.unmarkMemoryElement(objectTimeTree);
                        _spaceManager.unmarkMemoryElement(objectUniverseMap);
                        _spaceManager.unmarkMemoryElement(globalUniverseTree);
                        //free lock
                        if (!objectUniverseMap.tokenCompareAndSwap(magic, -1)) {
                            throw new RuntimeException("BadCompareAndSwap");
                        }
                        return clonedChunk;
                    } else {
                        //somebody as clone for us, now waiting for the chunk to be available
                        _spaceManager.unmarkMemoryElement(objectTimeTree);
                        _spaceManager.unmarkMemoryElement(objectUniverseMap);
                        _spaceManager.unmarkMemoryElement(globalUniverseTree);
                        KObjectChunk waitingChunk = (KObjectChunk) _spaceManager.getAndMark(universe, time, uuid);
                        _spaceManager.unmarkMemoryElement(waitingChunk);
                        //free lock
                        if (!objectUniverseMap.tokenCompareAndSwap(magic, -1)) {
                            throw new RuntimeException("BadCompareAndSwap");
                        }
                        return waitingChunk;
                    }
                }
            } else {
                _spaceManager.unmarkMemoryElement(objectTimeTree);
                _spaceManager.unmarkMemoryElement(globalUniverseTree);
                _spaceManager.unmarkMemoryElement(objectUniverseMap);
                //free lock
                if (!objectUniverseMap.tokenCompareAndSwap(magic, -1)) {
                    throw new RuntimeException("BadCompareAndSwap");
                }
                return null;
            }

        } catch (Throwable r) {
            //free lock
            r.printStackTrace();
            if (!objectUniverseMap.tokenCompareAndSwap(magic, -1)) {
                throw new RuntimeException("BadCompareAndSwap");
            }
            return null;
        }
    }

    @Override
    public void indexObject(KObject obj) {
        int metaClassIndex = obj.metaClass().index();
        short chunkType = KChunkTypes.OBJECT_CHUNK;
        if (metaClassIndex == MetaClassIndex.INSTANCE.index()) {
            chunkType = KChunkTypes.OBJECT_CHUNK_INDEX;
        }
        KObjectChunk cacheEntry = (KObjectChunk) _spaceManager.createAndMark(obj.universe(), obj.now(), obj.uuid(), chunkType);
        cacheEntry.init(null, _manager.model().metaModel(), metaClassIndex);
        cacheEntry.setFlags(KChunkFlags.DIRTY_BIT, 0);
        cacheEntry.space().declareDirty(cacheEntry);

        //initiate time management
        KLongTree timeTree = (KLongTree) _spaceManager.createAndMark(obj.universe(), KConfig.NULL_LONG, obj.uuid(), KChunkTypes.LONG_TREE);
        timeTree.init(null, _manager.model().metaModel(), metaClassIndex);
        timeTree.insertKey(obj.now());
        //initiate universe management
        KLongLongMap universeTree = (KLongLongMap) _spaceManager.createAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, obj.uuid(), KChunkTypes.LONG_LONG_MAP);
        universeTree.init(null, _manager.model().metaModel(), metaClassIndex);
        universeTree.put(obj.universe(), obj.now());
        _spaceManager.register(obj);
        //mark the global
        _spaceManager.getAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG);
    }

    public final void getOrLoadAndMark(long universe, long time, long uuid, final KCallback<KChunk> callback) {
        if (universe == KContentKey.NULL_KEY[0] && time == KContentKey.NULL_KEY[1] && uuid == KContentKey.NULL_KEY[2]) {
            callback.on(null);
            return;
        }
        KChunk cached = _spaceManager.getAndMark(universe, time, uuid);
        if (cached != null) {
            callback.on(cached);
        } else {
            load(new long[]{universe, time, uuid}, new KCallback<KChunk[]>() {
                @Override
                public void on(KChunk[] loadedElements) {
                    callback.on(loadedElements[0]);
                }
            });
        }
    }

    public final void getOrLoadAndMarkAll(long[] keys, final KCallback<KChunk[]> callback) {
        int nbKeys = keys.length / KEYS_SIZE;
        final boolean[] toLoadIndexes = new boolean[nbKeys];
        int nbElem = 0;
        final KChunk[] result = new KChunk[nbKeys];
        for (int i = 0; i < nbKeys; i++) {
            if (keys[i * KEYS_SIZE] == KContentKey.NULL_KEY[0] && keys[i * KEYS_SIZE + 1] == KContentKey.NULL_KEY[1] && keys[i * KEYS_SIZE + 2] == KContentKey.NULL_KEY[2]) {
                toLoadIndexes[i] = false;
                result[i] = null;
            } else {
                result[i] = _spaceManager.getAndMark(keys[i * KEYS_SIZE], keys[i * KEYS_SIZE + 1], keys[i * KEYS_SIZE + 2]);
                if (result[i] == null) {
                    toLoadIndexes[i] = true;
                    nbElem++;
                } else {
                    toLoadIndexes[i] = false;
                }
            }
        }
        if (nbElem == 0) {
            callback.on(result);
        } else {
            long[] keysToLoad = new long[nbElem * 3];
            int lastInsertedIndex = 0;
            for (int i = 0; i < nbKeys; i++) {
                if (toLoadIndexes[i]) {
                    keysToLoad[lastInsertedIndex] = keys[i * KEYS_SIZE];
                    lastInsertedIndex++;
                    keysToLoad[lastInsertedIndex] = keys[i * KEYS_SIZE + 1];
                    lastInsertedIndex++;
                    keysToLoad[lastInsertedIndex] = keys[i * KEYS_SIZE + 2];
                    lastInsertedIndex++;
                }
            }
            load(keysToLoad, new KCallback<KChunk[]>() {
                @Override
                public void on(KChunk[] loadedElements) {
                    int currentIndexToMerge = 0;
                    for (int i = 0; i < nbKeys; i++) {
                        if (toLoadIndexes[i]) {
                            result[i] = loadedElements[currentIndexToMerge];
                            currentIndexToMerge++;
                        }
                    }
                    callback.on(result);
                }
            });
        }
    }

    @Override
    public void resolveTimes(final long currentUniverse, final long currentUuid, final long startTime, final long endTime, KCallback<long[]> callback) {
        long[] keys = new long[]{
                KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG,
                KConfig.NULL_LONG, KConfig.NULL_LONG, currentUuid
        };
        getOrLoadAndMarkAll(keys, new KCallback<KChunk[]>() {
            @Override
            public void on(KChunk[] kMemoryChunks) {
                if (kMemoryChunks == null || kMemoryChunks.length == 0) {
                    callback.on(new long[0]);
                    return;
                }
                final long[] collectedUniverse = universeSelectByRange((KLongLongMap) kMemoryChunks[0], (KLongLongMap) kMemoryChunks[1], startTime, endTime, currentUniverse);
                int nbKeys = collectedUniverse.length * 3;
                final long[] timeTreeKeys = new long[nbKeys];
                for (int i = 0; i < collectedUniverse.length; i++) {
                    timeTreeKeys[i * 3] = collectedUniverse[i];
                    timeTreeKeys[i * 3 + 1] = KConfig.NULL_LONG;
                    timeTreeKeys[i * 3 + 2] = currentUuid;
                }
                final KLongLongMap objUniverse = (KLongLongMap) kMemoryChunks[1];
                getOrLoadAndMarkAll(timeTreeKeys, new KCallback<KChunk[]>() {
                    @Override
                    public void on(KChunk[] timeTrees) {
                        if (timeTrees == null || timeTrees.length == 0) {
                            _spaceManager.unmarkAllMemoryElements(kMemoryChunks);
                            callback.on(new long[0]);
                            return;
                        }
                        ArrayLongLongMap collector = new ArrayLongLongMap(-1, -1, -1, null);
                        long previousDivergenceTime = endTime;
                        for (int i = 0; i < collectedUniverse.length; i++) {
                            KLongTree timeTree = (KLongTree) timeTrees[i];
                            if (timeTree != null) {
                                long currentDivergenceTime = objUniverse.get(collectedUniverse[i]);
                                currentDivergenceTime = currentDivergenceTime > startTime ? currentDivergenceTime : startTime;
                                final long finalPreviousDivergenceTime = previousDivergenceTime;
                                timeTree.range(currentDivergenceTime, previousDivergenceTime, new KTreeWalker() {
                                    @Override
                                    public void elem(long t) {
                                        if (collector.size() == 0) {
                                            collector.put(collector.size(), t);
                                        } else {
                                            if (t != finalPreviousDivergenceTime) {
                                                collector.put(collector.size(), t);
                                            }
                                        }
                                    }
                                });
                                previousDivergenceTime = currentDivergenceTime;
                            }
                        }
                        long[] orderedTime = new long[collector.size()];
                        for (int i = 0; i < collector.size(); i++) {
                            orderedTime[i] = collector.get(i);
                        }
                        _spaceManager.unmarkAllMemoryElements(timeTrees);
                        _spaceManager.unmarkAllMemoryElements(kMemoryChunks);
                        callback.on(orderedTime);
                    }
                });
            }
        });
    }

    public static long resolve_universe(KLongLongMap globalTree, KLongLongMap objUniverseTree, long timeToResolve, long originUniverseId) {
        if (globalTree == null || objUniverseTree == null) {
            return originUniverseId;
        }
        long currentUniverse = originUniverseId;
        long previousUniverse = KConfig.NULL_LONG;
        long divergenceTime = objUniverseTree.get(currentUniverse);
        while (currentUniverse != previousUniverse) {
            //check range
            if (divergenceTime != KConfig.NULL_LONG && divergenceTime <= timeToResolve) {
                return currentUniverse;
            }
            //next round
            previousUniverse = currentUniverse;
            currentUniverse = globalTree.get(currentUniverse);
            divergenceTime = objUniverseTree.get(currentUniverse);
        }
        return originUniverseId;
    }

    public static long[] universeSelectByRange(KLongLongMap globalTree, KLongLongMap objUniverseTree, long rangeMin, long rangeMax, long originUniverseId) {
        KLongLongMap collected = new ArrayLongLongMap(-1, -1, -1, null);
        long currentUniverse = originUniverseId;
        long previousUniverse = KConfig.NULL_LONG;
        long divergenceTime = objUniverseTree.get(currentUniverse);
        while (currentUniverse != previousUniverse) {
            //check range
            if (divergenceTime != KConfig.NULL_LONG) {
                if (divergenceTime <= rangeMin) {
                    collected.put(collected.size(), currentUniverse);
                    break;
                } else if (divergenceTime <= rangeMax) {
                    collected.put(collected.size(), currentUniverse);
                }
            }
            //next round
            previousUniverse = currentUniverse;
            currentUniverse = globalTree.get(currentUniverse);
            divergenceTime = objUniverseTree.get(currentUniverse);
        }
        long[] trimmed = new long[collected.size()];
        for (long i = 0; i < collected.size(); i++) {
            trimmed[(int) i] = collected.get(i);
        }
        return trimmed;
    }

    private void load(long[] keys, KCallback<KChunk[]> callback) {
        this._manager.cdn().get(keys, new KCallback<String[]>() {
            @Override
            public void on(String[] payloads) {
                KChunk[] results = new KChunk[keys.length / 3];
                for (int i = 0; i < payloads.length; i++) {
                    long loopUniverse = keys[i * 3];
                    long loopTime = keys[i * 3 + 1];
                    long loopUuid = keys[i * 3 + 2];
                    short elemType;
                    if (loopUniverse == KConfig.NULL_LONG) {
                        elemType = KChunkTypes.LONG_LONG_MAP;
                    } else {
                        if (loopTime == KConfig.NULL_LONG) {
                            elemType = KChunkTypes.LONG_TREE;
                        } else {
                            if (payloads[i] == null || payloads[i].length() < 1) {
                                elemType = KChunkTypes.OBJECT_CHUNK;
                            } else {
                                char flag = payloads[i].charAt(0);
                                if (flag == '#') {
                                    elemType = KChunkTypes.OBJECT_CHUNK_INDEX;
                                } else {
                                    elemType = KChunkTypes.OBJECT_CHUNK;
                                }
                            }
                        }
                    }
                    results[i] = _spaceManager.createAndMark(loopUniverse, loopTime, loopUuid, elemType);
                    int classIndex = -1;
                    if (loopUniverse != KConfig.NULL_LONG && loopTime != KConfig.NULL_LONG && loopUuid != KConfig.NULL_LONG) {
                        KLongLongMap alreadyLoadedOrder = (KLongLongMap) _spaceManager.getAndMark(KConfig.NULL_LONG, KConfig.NULL_LONG, loopUuid);
                        if (alreadyLoadedOrder != null) {
                            classIndex = alreadyLoadedOrder.metaClassIndex();
                            _spaceManager.unmarkMemoryElement(alreadyLoadedOrder);
                        }
                    }
                    results[i].init(payloads[i], _manager.model().metaModel(), classIndex);
                }
                callback.on(results);
            }
        });
    }

    @Override
    public final int getRelatedKeysResultSize() {
        return 4;
    }

    @Override
    public void getRelatedKeys(long universe, long time, long uuid, long[] result) {

        result[0] = universe;
        result[1] = time;
        result[2] = uuid;

        result[3] = universe;
        result[4] = KConfig.NULL_LONG;
        result[5] = uuid;

        result[6] = KConfig.NULL_LONG;
        result[7] = KConfig.NULL_LONG;
        result[8] = uuid;

        result[9] = KConfig.NULL_LONG;
        result[10] = KConfig.NULL_LONG;
        result[11] = KConfig.NULL_LONG;

    }

}
