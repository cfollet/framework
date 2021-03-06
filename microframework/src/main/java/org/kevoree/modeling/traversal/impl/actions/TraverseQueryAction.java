package org.kevoree.modeling.traversal.impl.actions;

import org.kevoree.modeling.KObject;
import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.memory.chunk.KLongLongMap;
import org.kevoree.modeling.memory.chunk.KObjectChunk;
import org.kevoree.modeling.memory.chunk.impl.ArrayLongLongMap;
import org.kevoree.modeling.memory.chunk.KLongLongMapCallBack;
import org.kevoree.modeling.meta.KMeta;
import org.kevoree.modeling.meta.KMetaRelation;
import org.kevoree.modeling.meta.MetaType;
import org.kevoree.modeling.traversal.KTraversalAction;
import org.kevoree.modeling.traversal.KTraversalActionContext;
import org.kevoree.modeling.util.PrimitiveHelper;

public class TraverseQueryAction implements KTraversalAction {

    private final String SEP = ",";

    private KTraversalAction _next;

    private String _referenceQuery;

    public TraverseQueryAction(String p_referenceQuery) {
        this._referenceQuery = p_referenceQuery;
    }

    @Override
    public void chain(KTraversalAction p_next) {
        _next = p_next;
    }

    @Override
    public void execute(KTraversalActionContext context) {
        if (context.inputObjects() == null || context.inputObjects().length == 0) {
            if (_next != null) {
                _next.execute(context);
            } else {
                context.finalCallback().on(context.inputObjects());
            }
        } else {
            AbstractKObject currentFirstObject = (AbstractKObject) context.inputObjects()[0];
            KLongLongMap nextIds = new ArrayLongLongMap(-1, -1, -1, null);
            for (int i = 0; i < context.inputObjects().length; i++) {
                try {
                    AbstractKObject loopObj = (AbstractKObject) context.inputObjects()[i];
                    KObjectChunk raw = loopObj._manager.closestChunk(loopObj.universe(), loopObj.now(), loopObj.uuid(), loopObj.metaClass(), loopObj.previousResolved());
                    KMeta[] metaElements = loopObj.metaClass().metaElements();
                    if (raw != null) {
                        if (_referenceQuery == null) {
                            for (int j = 0; j < metaElements.length; j++) {
                                if (metaElements[j] != null && metaElements[j].metaType() == MetaType.RELATION) {
                                    long[] resolved = raw.getLongArray(metaElements[j].index(), loopObj.metaClass());
                                    if (resolved != null) {
                                        for (int k = 0; k < resolved.length; k++) {
                                            Long idResolved = resolved[k];
                                            nextIds.put(idResolved, idResolved);
                                        }
                                    }
                                }
                            }
                        } else {
                            String[] queries = _referenceQuery.split(SEP);
                            for (int k = 0; k < queries.length; k++) {
                                queries[k] = queries[k].replace("*", ".*");
                            }
                            for (int h = 0; h < metaElements.length; h++) {
                                if (metaElements[h] != null && metaElements[h].metaType() == MetaType.RELATION) {
                                    KMetaRelation metaReference = (KMetaRelation) metaElements[h];
                                    boolean selected = false;
                                    for (int k = 0; k < queries.length; k++) {
                                        if (queries[k] != null && PrimitiveHelper.startsWith(queries[k].trim(), "<<")) {
                                            if (PrimitiveHelper.matches(metaReference.oppositeName(), queries[k].trim().substring(2))) {
                                                selected = true;
                                                break;
                                            }
                                        } else {
                                            if (PrimitiveHelper.matches(metaReference.metaName(), "^" + queries[k] + "$")) {
                                                selected = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (selected) {
                                        long[] resolved = raw.getLongArray(metaElements[h].index(), loopObj.metaClass());
                                        if (resolved != null) {
                                            for (int j = 0; j < resolved.length; j++) {
                                                nextIds.put(resolved[j], resolved[j]);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            final long[] trimmed = new long[nextIds.size()];
            final int[] inserted = {0};
            nextIds.each(new KLongLongMapCallBack() {
                @Override
                public void on(long key, long value) {
                    trimmed[inserted[0]] = key;
                    inserted[0]++;
                }
            });
            //call
            currentFirstObject._manager.lookupAllObjects(currentFirstObject.universe(), currentFirstObject.now(), trimmed, new KCallback<KObject[]>() {
                @Override
                public void on(KObject[] nextStepElement) {
                    if (_next == null) {
                        context.finalCallback().on(nextStepElement);
                    } else {
                        context.setInputObjects(nextStepElement);
                        _next.execute(context);
                    }
                }
            });
        }
    }

}
