package org.kevoree.modeling.abs;

import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.KObjectInfer;
import org.kevoree.modeling.defer.KDefer;
import org.kevoree.modeling.memory.manager.KMemoryManager;
import org.kevoree.modeling.meta.*;
import org.kevoree.modeling.meta.impl.MetaLiteral;
import org.kevoree.modeling.traversal.KTraversalIndexResolver;

public class AbstractKObjectInfer extends AbstractKObject implements KObjectInfer {

    public AbstractKObjectInfer(long p_universe, long p_time, long p_uuid, KMetaClass p_metaClass, KMemoryManager p_manager) {
        super(p_universe, p_time, p_uuid, p_metaClass, p_manager);
    }

    private KTraversalIndexResolver dependenciesResolver(KObject[] dependencies) {
        return new KTraversalIndexResolver() {
            @Override
            public KObject[] resolve(String indexName) {
                KMetaDependency dependency = _metaClass.dependencies().dependencyByName(indexName);
                if (dependency != null) {
                    KObject[] single = new KObject[1];
                    single[0] = dependencies[dependency.index()];
                    return single;
                }
                return null;
            }
        };
    }

    @Override
    public void train(KObject[] dependencies, Object[] expectedOutputs, KCallback callback) {
        //wrap input
        KObject[][] all_dependencies = new KObject[1][dependencies.length];
        all_dependencies[0] = dependencies;
        //wrap output
        Object[][] all_expectedOutputs = null;
        if (expectedOutputs != null) {
            all_expectedOutputs = new Object[1][expectedOutputs.length];
            all_expectedOutputs[0] = expectedOutputs;
        }
        //call the trainAll method
        trainAll(all_dependencies, all_expectedOutputs, callback);
    }

    @Override
    public void trainAll(KObject[][] p_dependencies, Object[][] p_outputs, KCallback callback) {
        if (p_dependencies == null) {
            throw new RuntimeException("Dependencies are mandatory for KObjectInfer");
        }
        final KObjectInfer selfObject = this;
        KDefer waiter = this.manager().model().defer();
        for (int i = 0; i < p_dependencies.length; i++) {
            if (p_dependencies[i].length != _metaClass.dependencies().allDependencies().length) {
                throw new RuntimeException("Bad number of arguments for allDependencies");
            }
            KTraversalIndexResolver resolver = dependenciesResolver(p_dependencies[i]);
            for (int j = 0; j < _metaClass.inputs().length; j++) {
                _metaClass.inputs()[j].extractor().exec(null, resolver, waiter.wait(i + "," + j));
            }
        }
        waiter.then(new KCallback() {
            @Override
            public void on(Object o) {
                //collect output
                double[][] extractedInputs = new double[p_dependencies.length][_metaClass.inputs().length];
                for (int i = 0; i < p_dependencies.length; i++) {
                    for (int j = 0; j < _metaClass.inputs().length; j++) {
                        try {
                            Object[] extracted = (Object[]) waiter.getResult(i + "," + j);
                            if (extracted != null && extracted.length > 0) {
                                extractedInputs[i][j] = (double) extracted[0];
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                double[][] extractedOutputs = new double[1][_metaClass.outputs().length];
                for (int i = 0; i < p_dependencies.length; i++) {
                    for (int j = 0; j < _metaClass.outputs().length; j++) {
                        KMetaInferOutput metaInferOutput = _metaClass.outputs()[j];
                        Object currentOutputObject = null;
                        if (p_outputs != null) {
                            currentOutputObject = p_outputs[i][j];
                        }
                        extractedOutputs[i][j] = internalConvertOutput(currentOutputObject, metaInferOutput);
                    }
                }
                _metaClass.inferAlg().train(extractedInputs, extractedOutputs, selfObject);
                if (callback != null) {
                    callback.on(null);
                }
            }
        });
    }

    @Override
    public void infer(KObject[] dependencies, KCallback<Object[]> callback) {
        //wrap input
        KObject[][] all_dependencies = new KObject[1][dependencies.length];
        all_dependencies[0] = dependencies;
        //call the trainAll method
        inferAll(all_dependencies, new KCallback<Object[][]>() {
            @Override
            public void on(Object[][] objects) {
                if (objects != null && objects.length > 0) {
                    callback.on(objects[0]);
                } else {
                    callback.on(null);
                }
            }
        });
    }

    @Override
    public void inferAll(KObject[][] p_dependencies, KCallback<Object[][]> callback) {
        if (p_dependencies == null) {
            throw new RuntimeException("Bad number of arguments for allDependencies");
        }
        final KObjectInfer selfObject = this;
        KDefer waiter = this.manager().model().defer();
        for (int i = 0; i < p_dependencies.length; i++) {
            if (p_dependencies[i].length != _metaClass.dependencies().allDependencies().length) {
                throw new RuntimeException("Bad number of arguments for allDependencies");
            }
            KTraversalIndexResolver resolver = dependenciesResolver(p_dependencies[i]);
            for (int j = 0; j < _metaClass.inputs().length; j++) {
                _metaClass.inputs()[j].extractor().exec(null, resolver, waiter.wait(i + "," + j));
            }
        }
        waiter.then(new KCallback() {
            @Override
            public void on(Object o) {
                //collect output
                double[][] extractedInputs = new double[p_dependencies.length][_metaClass.inputs().length];
                for (int i = 0; i < p_dependencies.length; i++) {
                    extractedInputs[i] = new double[_metaClass.inputs().length];
                    for (int j = 0; j < _metaClass.inputs().length; j++) {
                        try {
                            Object[] extracted = (Object[]) waiter.getResult(i + "," + j);
                            if (extracted != null && extracted.length > 0) {
                                extractedInputs[i][j] = (double) extracted[0];
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                double[][] extractedOutputs = _metaClass.inferAlg().infer(extractedInputs, selfObject);
                if (extractedOutputs[0].length != _metaClass.outputs().length) {
                    callback.on(null);
                } else {
                    Object[][] result = new Object[extractedOutputs.length][_metaClass.outputs().length];
                    for (int i = 0; i < extractedOutputs.length; i++) {
                        result[i] = new Object[_metaClass.outputs().length];
                        for (int j = 0; j < _metaClass.outputs().length; j++) {
                            result[i][j] = internalReverseOutput(extractedOutputs[i][j], _metaClass.outputs()[j]);
                        }
                    }
                    callback.on(result);
                }
            }
        });
    }

    @Override
    public void resetLearning() {
        //TODO
        throw new RuntimeException("Not Implemented Yet!");
    }

    private double internalConvertOutput(Object output, KMetaInferOutput metaOutput) {
        if (output == null) {
            return 0;
        }
        if (metaOutput.type() == KPrimitiveTypes.BOOL) {
            if (output.equals(true)) {
                return 1.0;
            } else {
                return 0.0;
            }
        }
        if (metaOutput.type() == KPrimitiveTypes.DOUBLE) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.INT) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.FLOAT) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.CONTINUOUS) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.LONG) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.SHORT) {
            return (double) output;
        }
        if (metaOutput.type() == KPrimitiveTypes.STRING) {
            throw new RuntimeException("String are not managed yet");
        }
        if (metaOutput.type().isEnum()) {
            KMetaEnum metaEnum = (KMetaEnum) metaOutput.type();
            if (output instanceof MetaLiteral) {
                return (double) ((MetaLiteral) output).index();
            } else {
                KMeta literal = metaEnum.literalByName(output.toString());
                if (literal != null) {
                    return (double) literal.index();
                }
            }
        }
        return 0;
    }

    private Object internalReverseOutput(double inferred, KMetaInferOutput metaOutput) {
        if (metaOutput.type() == KPrimitiveTypes.BOOL) {
            if (inferred >= 0.5) {
                return true;
            } else {
                return false;
            }
        }
        if (metaOutput.type() == KPrimitiveTypes.DOUBLE) {
            return inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.INT) {
            return (int) inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.FLOAT) {
            return (float) inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.CONTINUOUS) {
            return inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.LONG) {
            return (long) inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.SHORT) {
            return (short) inferred;
        }
        if (metaOutput.type() == KPrimitiveTypes.STRING) {
            throw new RuntimeException("String are not managed yet");
        }
        if (metaOutput.type().isEnum()) {
            int ceiledInferred = math_ceil(inferred);
            KMetaEnum metaEnum = (KMetaEnum) metaOutput.type();
            return metaEnum.literal(ceiledInferred);
        }
        return null;
    }

    /** @native ts
     * return Math.round(toCeilValue);
     * */
    private int math_ceil(double toCeilValue){
        return (int) Math.round(toCeilValue);
    }


}
