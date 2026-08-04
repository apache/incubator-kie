/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools;

import java.io.Serializable;
import java.util.Arrays;

public class Primitives
        implements Serializable {

    public Primitives() {
    }

    public boolean isBooleanPrimitive() {
        return booleanPrimitive;
    }

    public void setBooleanPrimitive(boolean booleanPrimitive) {
        this.booleanPrimitive = booleanPrimitive;
    }

    public byte getBytePrimitive() {
        return bytePrimitive;
    }

    public void setBytePrimitive(byte bytePrimitive) {
        this.bytePrimitive = bytePrimitive;
    }

    public char getCharPrimitive() {
        return charPrimitive;
    }

    public void setCharPrimitive(char charPrimitive) {
        this.charPrimitive = charPrimitive;
    }

    public double getDoublePrimitive() {
        return doublePrimitive;
    }

    public void setDoublePrimitive(double doublePrimitive) {
        this.doublePrimitive = doublePrimitive;
    }

    public float getFloatPrimitive() {
        return floatPrimitive;
    }

    public void setFloatPrimitive(float floatPrimitive) {
        this.floatPrimitive = floatPrimitive;
    }

    public int getIntPrimitive() {
        return intPrimitive;
    }

    public void setIntPrimitive(int intPrimitive) {
        this.intPrimitive = intPrimitive;
    }

    public long getLongPrimitive() {
        return longPrimitive;
    }

    public void setLongPrimitive(long longPrimitive) {
        this.longPrimitive = longPrimitive;
    }

    public short getShortPrimitive() {
        return shortPrimitive;
    }

    public void setShortPrimitive(short shortPrimitive) {
        this.shortPrimitive = shortPrimitive;
    }

    public Object[] getArrayAttribute() {
        return arrayAttribute;
    }

    public void setArrayAttribute(Object arrayAttribute[]) {
        this.arrayAttribute = arrayAttribute;
    }

    public boolean[] getPrimitiveBooleanArray() {
        return primitiveBooleanArray;
    }

    public void setPrimitiveBooleanArray(boolean primitiveBooleanArray[]) {
        this.primitiveBooleanArray = primitiveBooleanArray;
    }

    public byte[] getPrimitiveByteArray() {
        return primitiveByteArray;
    }

    public void setPrimitiveByteArray(byte primitiveByteArray[]) {
        this.primitiveByteArray = primitiveByteArray;
    }

    public short[] getPrimitiveShortArray() {
        return primitiveShortArray;
    }

    public void setPrimitiveShortArray(short primitiveShortArray[]) {
        this.primitiveShortArray = primitiveShortArray;
    }

    public char[] getPrimitiveCharArray() {
        return primitiveCharArray;
    }

    public void setPrimitiveCharArray(char primitiveCharArray[]) {
        this.primitiveCharArray = primitiveCharArray;
    }

    public int[] getPrimitiveIntArray() {
        return primitiveIntArray;
    }

    public void setPrimitiveIntArray(int primitiveArrayAttribute[]) {
        primitiveIntArray = primitiveArrayAttribute;
    }

    public long[] getPrimitiveLongArray() {
        return primitiveLongArray;
    }

    public void setPrimitiveLongArray(long primitiveLongArray[]) {
        this.primitiveLongArray = primitiveLongArray;
    }

    public float[] getPrimitiveFloatArray() {
        return primitiveFloatArray;
    }

    public void setPrimitiveFloatArray(float floatDoubleArray[]) {
        primitiveFloatArray = floatDoubleArray;
    }

    public double[] getPrimitiveDoubleArray() {
        return primitiveDoubleArray;
    }

    public void setPrimitiveDoubleArray(double primitiveDoubleArrayAttribute[]) {
        primitiveDoubleArray = primitiveDoubleArrayAttribute;
    }

    public String[] getStringArray() {
        return stringArray;
    }

    public void setStringArray(String stringArray[]) {
        this.stringArray = stringArray;
    }

    public String getStringAttribute() {
        return stringAttribute;
    }

    public void setStringAttribute(String stringAttribute) {
        this.stringAttribute = stringAttribute;
    }

    public Boolean getBooleanWrapper() {
        return booleanWrapper;
    }

    public void setBooleanWrapper(Boolean booleanWrapper) {
        this.booleanWrapper = booleanWrapper;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = 31 * result + Arrays.hashCode(arrayAttribute);
        result = 31 * result + (booleanPrimitive ? 1231 : '\u04D5');
        result = 31 * result + (booleanWrapper != null ? booleanWrapper.hashCode() : 0);
        result = 31 * result + charPrimitive;
        long doubeAsLongBits = Double.doubleToLongBits(doublePrimitive);
        result = 31 * result + (int) (doubeAsLongBits ^ doubeAsLongBits >>> 32);
        result = 31 * result + Float.floatToIntBits(floatPrimitive);
        result = 31 * result + intPrimitive;
        result = 31 * result + (int) (longPrimitive ^ longPrimitive >>> 32);
        result = 31 * result + (object != null ? object.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(primitiveIntArray);
        result = 31 * result + Arrays.hashCode(primitiveDoubleArray);
        result = 31 * result + shortPrimitive;
        result = 31 * result + Arrays.hashCode(stringArray);
        result = 31 * result + (stringAttribute != null ? stringAttribute.hashCode() : 0);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Primitives other = (Primitives) obj;
        if (!Arrays.equals(arrayAttribute, other.arrayAttribute)) {
            return false;
        }
        if (booleanPrimitive != other.booleanPrimitive) {
            return false;
        }
        if (booleanWrapper == null) {
            if (other.booleanWrapper != null) {
                return false;
            }
        } else if (!booleanWrapper.equals(other.booleanWrapper)) {
            return false;
        }
        if (bytePrimitive != other.bytePrimitive) {
            return false;
        }
        if (charPrimitive != other.charPrimitive) {
            return false;
        }
        if (Double.doubleToLongBits(doublePrimitive) != Double.doubleToLongBits(other.doublePrimitive)) {
            return false;
        }
        if (Float.floatToIntBits(floatPrimitive) != Float.floatToIntBits(other.floatPrimitive)) {
            return false;
        }
        if (intPrimitive != other.intPrimitive) {
            return false;
        }
        if (longPrimitive != other.longPrimitive) {
            return false;
        }
        if (object == null) {
            if (other.object != null) {
                return false;
            }
        } else if (!object.equals(other.object)) {
            return false;
        }
        if (!Arrays.equals(primitiveIntArray, other.primitiveIntArray)) {
            return false;
        }
        if (!Arrays.equals(primitiveDoubleArray, other.primitiveDoubleArray)) {
            return false;
        }
        if (shortPrimitive != other.shortPrimitive) {
            return false;
        }
        if (!Arrays.equals(stringArray, other.stringArray)) {
            return false;
        }
        if (stringAttribute == null) {
            if (other.stringAttribute != null) {
                return false;
            }
        } else if (!stringAttribute.equals(other.stringAttribute)) {
            return false;
        }
        return true;
    }

    public TestEnum getEnumValue() {
        return enumValue;
    }

    public void setEnumValue(TestEnum enumValue) {
        this.enumValue = enumValue;
    }

    private static final long serialVersionUID = 0xd646cef8675cdfa2L;
    private boolean booleanPrimitive;
    private char charPrimitive;
    private byte bytePrimitive;
    private short shortPrimitive;
    private int intPrimitive;
    private long longPrimitive;
    private float floatPrimitive;
    private double doublePrimitive;
    private String stringAttribute;
    private Object arrayAttribute[];
    private boolean primitiveBooleanArray[];
    private byte primitiveByteArray[];
    private short primitiveShortArray[];
    private char primitiveCharArray[];
    private int primitiveIntArray[];
    private long primitiveLongArray[];
    private float primitiveFloatArray[];
    private double primitiveDoubleArray[];
    private String stringArray[];
    private Boolean booleanWrapper;
    private Object object;
    private TestEnum enumValue;
}