package org.kevoree.modeling.memory.chunk;

import org.kevoree.modeling.memory.KChunk;
import org.kevoree.modeling.meta.KMetaClass;
import org.kevoree.modeling.meta.KMetaModel;

public interface KObjectChunk extends KChunk {

    /* Stateful Management */
    KObjectChunk clone(long p_universe, long p_time, long p_obj, KMetaModel p_metaClass);

    int metaClassIndex();

    String toJSON(KMetaModel metaModel);

    /* PrimitiveType Single Management */
    void setPrimitiveType(int index, Object content, KMetaClass metaClass);

    Object getPrimitiveType(int index, KMetaClass metaClass);

    /* LongArray Management */
    long[] getLongArray(int index, KMetaClass metaClass);

    int getLongArraySize(int index, KMetaClass metaClass);

    long getLongArrayElem(int index, int refIndex, KMetaClass metaClass);

    boolean addLongToArray(int index, long newRef, KMetaClass metaClass);

    boolean removeLongToArray(int index, long previousRef, KMetaClass metaClass);

    void clearLongArray(int index, KMetaClass metaClass);

    /* DoubleArray Management */
    double[] getDoubleArray(int index, KMetaClass metaClass);

    int getDoubleArraySize(int index, KMetaClass metaClass);

    double getDoubleArrayElem(int index, int arrayIndex, KMetaClass metaClass);

    void setDoubleArrayElem(int index, int arrayIndex, double valueToInsert, KMetaClass metaClass);

    void extendDoubleArray(int index, int newSize, KMetaClass metaClass);

    void clearDoubleArray(int index, KMetaClass metaClass);

}
