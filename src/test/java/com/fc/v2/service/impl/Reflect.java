package com.fc.v2.service.impl;

import java.lang.reflect.Field;

/**
 * 测试反射工具：向 service 的私有 mapper 字段注入内存假实现。
 */
final class Reflect {

    private Reflect() {
    }

    static void setField(Object target, String fieldName, Object value) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("field not found: " + fieldName);
    }

    static java.util.Date parse(String s) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
