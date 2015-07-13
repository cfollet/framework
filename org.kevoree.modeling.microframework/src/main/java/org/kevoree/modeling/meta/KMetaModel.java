package org.kevoree.modeling.meta;

import org.kevoree.modeling.KModel;
import org.kevoree.modeling.KType;
import org.kevoree.modeling.infer.KInferAlg;

public interface KMetaModel extends KMeta {

    KMetaClass[] metaClasses();

    KMetaClass metaClassByName(String name);

    KMetaClass metaClass(int index);

    KMetaClass addMetaClass(String metaClassName);

    KMetaClass addInferMetaClass(String metaClassName, KInferAlg inferAlg);

    KMetaEnum[] metaTypes();

    KMetaEnum metaTypeByName(String name);

    KMetaEnum addMetaEnum(String enumName);

    KModel model();

}
