package org.kevoree.modeling.meta.impl;

import org.kevoree.modeling.extrapolation.Extrapolation;
import org.kevoree.modeling.extrapolation.impl.DiscreteExtrapolation;
import org.kevoree.modeling.meta.KMetaAttribute;
import org.kevoree.modeling.meta.MetaType;

public class MetaAttribute implements KMetaAttribute {

    private String _name;

    private int _index;

    public double _precision;

    private boolean _key;

    private int _attributeTypeId;

    private Extrapolation _extrapolation;

    @Override
    public int attributeTypeId() {
        return _attributeTypeId;
    }

    @Override
    public int index() {
        return _index;
    }

    @Override
    public String metaName() {
        return _name;
    }

    @Override
    public MetaType metaType() {
        return MetaType.ATTRIBUTE;
    }

    @Override
    public double precision() {
        return _precision;
    }

    @Override
    public boolean key() {
        return _key;
    }

    @Override
    public Extrapolation strategy() {
        return _extrapolation;
    }

    @Override
    public void setExtrapolation(Extrapolation extrapolation) {
        this._extrapolation = extrapolation;
    }

    @Override
    public void setPrecision(double p_precision) {
        this._precision = p_precision;
    }

    @Override
    public void setKey(boolean p_key) {
        this._key = p_key;
    }

    public MetaAttribute(String p_name, int p_index, double p_precision, boolean p_key, int p_attributeTypeId, Extrapolation p_extrapolation) {
        this._name = p_name;
        this._index = p_index;
        this._precision = p_precision;
        this._key = p_key;
        this._attributeTypeId = p_attributeTypeId;
        this._extrapolation = p_extrapolation;
        if (this._extrapolation == null) {
            this._extrapolation = DiscreteExtrapolation.instance();
        }
    }


}
