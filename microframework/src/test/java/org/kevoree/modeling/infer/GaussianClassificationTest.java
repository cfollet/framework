package org.kevoree.modeling.infer;

import org.junit.Assert;
import org.junit.Test;
import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KModel;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.KObjectInfer;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.infer.impl.GaussianClassifierAlg;
import org.kevoree.modeling.memory.chunk.KObjectChunk;
import org.kevoree.modeling.memory.manager.DataManagerBuilder;
import org.kevoree.modeling.memory.manager.internal.KInternalDataManager;
import org.kevoree.modeling.meta.KMetaClass;
import org.kevoree.modeling.meta.KMetaEnum;
import org.kevoree.modeling.meta.KMetaModel;
import org.kevoree.modeling.meta.KPrimitiveTypes;
import org.kevoree.modeling.meta.impl.MetaModel;
import org.kevoree.modeling.scheduler.impl.DirectScheduler;

public class GaussianClassificationTest {
    private KMetaModel createMetaModel() {
        KMetaModel metaModel = new MetaModel("GaussianClassificationTestMM");


        KMetaClass metaClassIris = metaModel.addMetaClass("Iris");
        metaClassIris.addAttribute("sepalLength", KPrimitiveTypes.DOUBLE);
        metaClassIris.addAttribute("sepalWidth", KPrimitiveTypes.DOUBLE);
        metaClassIris.addAttribute("petalLength", KPrimitiveTypes.DOUBLE);
        metaClassIris.addAttribute("petalWidth", KPrimitiveTypes.DOUBLE);

        KMetaEnum metaEnumIrisType = metaModel.addMetaEnum("IrisType");
        metaEnumIrisType.addLiteral("IRISSETOSA");
        metaEnumIrisType.addLiteral("IRISVERSICOLOUR");
        metaEnumIrisType.addLiteral("IRISVIRGINICA");


        metaClassIris.addAttribute("type", metaEnumIrisType);

        KMetaClass inferGaussian = metaModel.addInferMetaClass("GaussianProfile", new GaussianClassifierAlg());
        inferGaussian.addDependency("Iris", metaClassIris.index());

        inferGaussian.addInput("Iris", "=sepalLength");
        inferGaussian.addInput("Iris", "=sepalWidth");
        inferGaussian.addInput("Iris", "=petalLength");
        inferGaussian.addInput("Iris", "=petalWidth");

        inferGaussian.addOutput("type", metaEnumIrisType);

        return metaModel;
    }

    @Test
    public void test() {

        final double[] irisdataset = {
                5.1, 3.5, 1.4, 0.2, 0,
                4.9, 3.0, 1.4, 0.2, 0,
                4.7, 3.2, 1.3, 0.2, 0,
                4.6, 3.1, 1.5, 0.2, 0,
                5.0, 3.6, 1.4, 0.2, 0,
                5.4, 3.9, 1.7, 0.4, 0,
                4.6, 3.4, 1.4, 0.3, 0,
                5.0, 3.4, 1.5, 0.2, 0,
                4.4, 2.9, 1.4, 0.2, 0,
                4.9, 3.1, 1.5, 0.1, 0,
                5.4, 3.7, 1.5, 0.2, 0,
                4.8, 3.4, 1.6, 0.2, 0,
                4.8, 3.0, 1.4, 0.1, 0,
                4.3, 3.0, 1.1, 0.1, 0,
                5.8, 4.0, 1.2, 0.2, 0,
                5.7, 4.4, 1.5, 0.4, 0,
                5.4, 3.9, 1.3, 0.4, 0,
                5.1, 3.5, 1.4, 0.3, 0,
                5.7, 3.8, 1.7, 0.3, 0,
                5.1, 3.8, 1.5, 0.3, 0,
                5.4, 3.4, 1.7, 0.2, 0,
                5.1, 3.7, 1.5, 0.4, 0,
                4.6, 3.6, 1.0, 0.2, 0,
                5.1, 3.3, 1.7, 0.5, 0,
                4.8, 3.4, 1.9, 0.2, 0,
                5.0, 3.0, 1.6, 0.2, 0,
                5.0, 3.4, 1.6, 0.4, 0,
                5.2, 3.5, 1.5, 0.2, 0,
                5.2, 3.4, 1.4, 0.2, 0,
                4.7, 3.2, 1.6, 0.2, 0,
                4.8, 3.1, 1.6, 0.2, 0,
                5.4, 3.4, 1.5, 0.4, 0,
                5.2, 4.1, 1.5, 0.1, 0,
                5.5, 4.2, 1.4, 0.2, 0,
                4.9, 3.1, 1.5, 0.1, 0,
                5.0, 3.2, 1.2, 0.2, 0,
                5.5, 3.5, 1.3, 0.2, 0,
                4.9, 3.1, 1.5, 0.1, 0,
                4.4, 3.0, 1.3, 0.2, 0,
                5.1, 3.4, 1.5, 0.2, 0,
                5.0, 3.5, 1.3, 0.3, 0,
                4.5, 2.3, 1.3, 0.3, 0,
                4.4, 3.2, 1.3, 0.2, 0,
                5.0, 3.5, 1.6, 0.6, 0,
                5.1, 3.8, 1.9, 0.4, 0,
                4.8, 3.0, 1.4, 0.3, 0,
                5.1, 3.8, 1.6, 0.2, 0,
                4.6, 3.2, 1.4, 0.2, 0,
                5.3, 3.7, 1.5, 0.2, 0,
                5.0, 3.3, 1.4, 0.2, 0,
                7.0, 3.2, 4.7, 1.4, 1,
                6.4, 3.2, 4.5, 1.5, 1,
                6.9, 3.1, 4.9, 1.5, 1,
                5.5, 2.3, 4.0, 1.3, 1,
                6.5, 2.8, 4.6, 1.5, 1,
                5.7, 2.8, 4.5, 1.3, 1,
                6.3, 3.3, 4.7, 1.6, 1,
                4.9, 2.4, 3.3, 1.0, 1,
                6.6, 2.9, 4.6, 1.3, 1,
                5.2, 2.7, 3.9, 1.4, 1,
                5.0, 2.0, 3.5, 1.0, 1,
                5.9, 3.0, 4.2, 1.5, 1,
                6.0, 2.2, 4.0, 1.0, 1,
                6.1, 2.9, 4.7, 1.4, 1,
                5.6, 2.9, 3.6, 1.3, 1,
                6.7, 3.1, 4.4, 1.4, 1,
                5.6, 3.0, 4.5, 1.5, 1,
                5.8, 2.7, 4.1, 1.0, 1,
                6.2, 2.2, 4.5, 1.5, 1,
                5.6, 2.5, 3.9, 1.1, 1,
                5.9, 3.2, 4.8, 1.8, 1,
                6.1, 2.8, 4.0, 1.3, 1,
                6.3, 2.5, 4.9, 1.5, 1,
                6.1, 2.8, 4.7, 1.2, 1,
                6.4, 2.9, 4.3, 1.3, 1,
                6.6, 3.0, 4.4, 1.4, 1,
                6.8, 2.8, 4.8, 1.4, 1,
                6.7, 3.0, 5.0, 1.7, 1,
                6.0, 2.9, 4.5, 1.5, 1,
                5.7, 2.6, 3.5, 1.0, 1,
                5.5, 2.4, 3.8, 1.1, 1,
                5.5, 2.4, 3.7, 1.0, 1,
                5.8, 2.7, 3.9, 1.2, 1,
                6.0, 2.7, 5.1, 1.6, 1,
                5.4, 3.0, 4.5, 1.5, 1,
                6.0, 3.4, 4.5, 1.6, 1,
                6.7, 3.1, 4.7, 1.5, 1,
                6.3, 2.3, 4.4, 1.3, 1,
                5.6, 3.0, 4.1, 1.3, 1,
                5.5, 2.5, 4.0, 1.3, 1,
                5.5, 2.6, 4.4, 1.2, 1,
                6.1, 3.0, 4.6, 1.4, 1,
                5.8, 2.6, 4.0, 1.2, 1,
                5.0, 2.3, 3.3, 1.0, 1,
                5.6, 2.7, 4.2, 1.3, 1,
                5.7, 3.0, 4.2, 1.2, 1,
                5.7, 2.9, 4.2, 1.3, 1,
                6.2, 2.9, 4.3, 1.3, 1,
                5.1, 2.5, 3.0, 1.1, 1,
                5.7, 2.8, 4.1, 1.3, 1,
                6.3, 3.3, 6.0, 2.5, 2,
                5.8, 2.7, 5.1, 1.9, 2,
                7.1, 3.0, 5.9, 2.1, 2,
                6.3, 2.9, 5.6, 1.8, 2,
                6.5, 3.0, 5.8, 2.2, 2,
                7.6, 3.0, 6.6, 2.1, 2,
                4.9, 2.5, 4.5, 1.7, 2,
                7.3, 2.9, 6.3, 1.8, 2,
                6.7, 2.5, 5.8, 1.8, 2,
                7.2, 3.6, 6.1, 2.5, 2,
                6.5, 3.2, 5.1, 2.0, 2,
                6.4, 2.7, 5.3, 1.9, 2,
                6.8, 3.0, 5.5, 2.1, 2,
                5.7, 2.5, 5.0, 2.0, 2,
                5.8, 2.8, 5.1, 2.4, 2,
                6.4, 3.2, 5.3, 2.3, 2,
                6.5, 3.0, 5.5, 1.8, 2,
                7.7, 3.8, 6.7, 2.2, 2,
                7.7, 2.6, 6.9, 2.3, 2,
                6.0, 2.2, 5.0, 1.5, 2,
                6.9, 3.2, 5.7, 2.3, 2,
                5.6, 2.8, 4.9, 2.0, 2,
                7.7, 2.8, 6.7, 2.0, 2,
                6.3, 2.7, 4.9, 1.8, 2,
                6.7, 3.3, 5.7, 2.1, 2,
                7.2, 3.2, 6.0, 1.8, 2,
                6.2, 2.8, 4.8, 1.8, 2,
                6.1, 3.0, 4.9, 1.8, 2,
                6.4, 2.8, 5.6, 2.1, 2,
                7.2, 3.0, 5.8, 1.6, 2,
                7.4, 2.8, 6.1, 1.9, 2,
                7.9, 3.8, 6.4, 2.0, 2,
                6.4, 2.8, 5.6, 2.2, 2,
                6.3, 2.8, 5.1, 1.5, 2,
                6.1, 2.6, 5.6, 1.4, 2,
                7.7, 3.0, 6.1, 2.3, 2,
                6.3, 3.4, 5.6, 2.4, 2,
                6.4, 3.1, 5.5, 1.8, 2,
                6.0, 3.0, 4.8, 1.8, 2,
                6.9, 3.1, 5.4, 2.1, 2,
                6.7, 3.1, 5.6, 2.4, 2,
                6.9, 3.1, 5.1, 2.3, 2,
                5.8, 2.7, 5.1, 1.9, 2,
                6.8, 3.2, 5.9, 2.3, 2,
                6.7, 3.3, 5.7, 2.5, 2,
                6.7, 3.0, 5.2, 2.3, 2,
                6.3, 2.5, 5.0, 1.9, 2,
                6.5, 3.0, 5.2, 2.0, 2,
                6.2, 3.4, 5.4, 2.3, 2,
                5.9, 3.0, 5.1, 1.8, 2};


        KMetaModel mm = createMetaModel();
        KModel model = mm.createModel(DataManagerBuilder.create().withScheduler(new DirectScheduler()).build());
        model.connect(new KCallback() {
            @Override
            public void on(Object o) {

                KObject newObj = model.createByName("GaussianProfile", 0, 0);
                KObjectInfer gaussianProfile = (KObjectInfer) newObj;

                for (int i = 0; i < irisdataset.length; i += 5) {

                    KObject irisInstance = model.createByName("Iris", 0, 0);
                    irisInstance.setByName("sepalLength", irisdataset[i]);
                    irisInstance.setByName("sepalWidth", irisdataset[i + 1]);
                    irisInstance.setByName("petalLength", irisdataset[i + 2]);
                    irisInstance.setByName("petalWidth", irisdataset[i + 3]);
                    irisInstance.setByName("type", (mm.metaTypeByName("IrisType")).literal((int) irisdataset[i + 4]));

                    Object[] output = new Object[1];
                    output[0] = irisInstance.getByName("type");
                    gaussianProfile.genericTrain(new KObject[]{irisInstance}, output, null);
                }

                KObject[][] irisInstanceTest = new KObject[3][1];
                // Test class 0
                irisInstanceTest[0] = new KObject[1];
                irisInstanceTest[0][0] = model.createByName("Iris", 0, 0);
                irisInstanceTest[0][0].setByName("sepalLength", 5.006);
                irisInstanceTest[0][0].setByName("sepalWidth", 3.418);
                irisInstanceTest[0][0].setByName("petalLength", 1.464);
                irisInstanceTest[0][0].setByName("petalWidth", 0.244);

                // Test class 1
                irisInstanceTest[1] = new KObject[1];
                irisInstanceTest[1][0] = model.createByName("Iris", 0, 0);
                irisInstanceTest[1][0].setByName("sepalLength", 5.936);
                irisInstanceTest[1][0].setByName("sepalWidth", 2.77);
                irisInstanceTest[1][0].setByName("petalLength", 4.26);
                irisInstanceTest[1][0].setByName("petalWidth", 1.326);

                // Test class 2
                irisInstanceTest[2] = new KObject[1];
                irisInstanceTest[2][0] = model.createByName("Iris", 0, 0);
                irisInstanceTest[2][0].setByName("sepalLength", 6.588);
                irisInstanceTest[2][0].setByName("sepalWidth", 2.974);
                irisInstanceTest[2][0].setByName("petalLength", 5.552);
                irisInstanceTest[2][0].setByName("petalWidth", 2.026);


                //for debug
                KObjectChunk ks = ((KInternalDataManager) model.manager()).preciseChunk(0, 0, gaussianProfile.uuid(), gaussianProfile.metaClass(), ((AbstractKObject) newObj).previousResolved());

                gaussianProfile.genericInferAll(irisInstanceTest, new KCallback<Object[][]>() {
                    @Override
                    public void on(Object[][] objects) {
                        //to replace by iris type later
                        Assert.assertTrue(objects[0][0] == (mm.metaTypeByName("IrisType")).literalByName("IRISSETOSA"));
                        Assert.assertTrue(objects[1][0] == (mm.metaTypeByName("IrisType")).literalByName("IRISVERSICOLOUR"));
                        Assert.assertTrue(objects[2][0] == (mm.metaTypeByName("IrisType")).literalByName("IRISVIRGINICA"));
                    }
                });
            }

        });


    }
}
