package com.fc.v2.service.impl;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 测试专用：内存版 BaseMapper。解释 QueryWrapper/UpdateWrapper 里的
 * eq/ne 谓词（本仓 topon 业务只用到这两种），让 service 单测无需数据库。
 * updateById / update(entity, wrapper) 遵循 MyBatis-Plus 默认 NOT_NULL 策略：
 * 仅拷贝非空字段。
 */
public class FakeBaseMapper<T> implements BaseMapper<T> {

    private static final Pattern PRED = Pattern.compile(
            "([a-zA-Z0-9_]+)\\s*(=|<>)\\s*#\\{ew\\.paramNameValuePairs\\.([a-zA-Z0-9_]+)\\}");

    private final Map<Serializable, T> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong(1000L);

    /** 测试塞数据：直接放入（id 取自实体本身，为空则补发） */
    public T seed(T entity) {
        Serializable id = idOf(entity);
        if (id == null) {
            id = seq.incrementAndGet();
            writeId(entity, id);
        }
        store.put(id, entity);
        return entity;
    }

    public List<T> all() {
        return new ArrayList<>(store.values());
    }

    public T stored(Serializable id) {
        return store.get(id);
    }

    public int size() {
        return store.size();
    }

    /**
     * 生成具体 mapper 接口（如 TToponCheckTaskMapper）的动态代理，
     * 调用全部转发到本内存实现，供反射注入具体类型的 service 字段。
     */
    @SuppressWarnings("unchecked")
    public <M> M as(Class<M> mapperInterface) {
        return (M) java.lang.reflect.Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[] {mapperInterface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    Method target = FakeBaseMapper.class.getMethod(
                            method.getName(), method.getParameterTypes());
                    return target.invoke(this, args);
                });
    }

    // ---------------- BaseMapper ----------------

    @Override
    public int insert(T entity) {
        Serializable id = idOf(entity);
        if (id == null) {
            id = seq.incrementAndGet();
            writeId(entity, id);
        }
        store.put(id, entity);
        return 1;
    }

    @Override
    public int deleteById(Serializable id) {
        return store.remove(id) == null ? 0 : 1;
    }

    @Override
    public int delete(Wrapper<T> wrapper) {
        int n = 0;
        for (T row : matched(wrapper)) {
            if (store.remove(idOf(row)) != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int deleteBatchIds(java.util.Collection<? extends Serializable> idList) {
        int n = 0;
        for (Object id : idList) {
            if (store.remove(id) != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int updateById(T entity) {
        Serializable id = idOf(entity);
        T old = store.get(id);
        if (old == null) {
            return 0;
        }
        copyNotNull(entity, old);
        return 1;
    }

    @Override
    public int update(T entity, Wrapper<T> wrapper) {
        int n = 0;
        for (T row : matched(wrapper)) {
            copyNotNull(entity, row);
            n++;
        }
        return n;
    }

    @Override
    public T selectById(Serializable id) {
        return store.get(id);
    }

    @Override
    public List<T> selectBatchIds(java.util.Collection<? extends Serializable> idList) {
        List<T> out = new ArrayList<>();
        for (Object id : idList) {
            T row = store.get(id);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    @Override
    public T selectOne(Wrapper<T> wrapper) {
        List<T> rows = selectList(wrapper);
        if (rows.size() > 1) {
            throw new RuntimeException("TooManyResults: " + rows.size());
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public Integer selectCount(Wrapper<T> wrapper) {
        return Integer.valueOf(matched(wrapper).size());
    }

    @Override
    public List<T> selectList(Wrapper<T> wrapper) {
        return matched(wrapper);
    }

    // ---------------- 未用到的方法 ----------------

    @Override
    public int deleteByMap(java.util.Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> selectByMap(java.util.Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<java.util.Map<String, Object>> selectMaps(Wrapper<T> wrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> selectObjs(Wrapper<T> wrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E extends com.baomidou.mybatisplus.core.metadata.IPage<T>> E selectPage(
            E page, Wrapper<T> wrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E extends com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>>> E selectMapsPage(
            E page, Wrapper<T> wrapper) {
        throw new UnsupportedOperationException();
    }

    // ---------------- 谓词解释 ----------------

    private List<T> matched(Wrapper<T> wrapper) {
        List<T> out = new ArrayList<>();
        if (wrapper == null) {
            out.addAll(store.values());
            return out;
        }
        List<Object[]> preds = predicatesOf(wrapper);
        for (T row : store.values()) {
            boolean ok = true;
            for (Object[] p : preds) {
                String column = (String) p[0];
                String op = (String) p[1];
                Object expect = p[2];
                Object actual = readColumn(row, column);
                boolean eq = valueEquals(actual, expect);
                if ("=".equals(op) ? !eq : eq) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(row);
            }
        }
        return out;
    }

    /** 解析 AbstractWrapper 渲染出的 `col = #{ew.paramNameValuePairs.xxx}` 片段（仅 AND+eq/ne） */
    @SuppressWarnings("unchecked")
    private static List<Object[]> predicatesOf(Wrapper<?> wrapper) {
        List<Object[]> preds = new ArrayList<>();
        String sql = wrapper.getSqlSegment();
        if (sql == null || sql.trim().isEmpty()) {
            return preds;
        }
        Map<String, Object> params;
        try {
            Field f = AbstractWrapper.class.getDeclaredField("paramNameValuePairs");
            f.setAccessible(true);
            params = (Map<String, Object>) f.get(wrapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        Matcher m = PRED.matcher(sql);
        while (m.find()) {
            preds.add(new Object[] {m.group(1), m.group(2), params.get(m.group(3))});
        }
        return preds;
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a instanceof Number && b instanceof Number) {
            return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString())) == 0;
        }
        return a.equals(b);
    }

    // ---------------- 实体反射工具 ----------------

    private static Serializable idOf(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object v = getId.invoke(entity);
            return (Serializable) v;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeId(Object entity, Serializable id) {
        try {
            Method setId = null;
            for (Method c : entity.getClass().getMethods()) {
                if (c.getName().equals("setId") && c.getParameterCount() == 1) {
                    setId = c;
                    break;
                }
            }
            if (setId == null) {
                throw new IllegalStateException("no setId");
            }
            Class<?> t = setId.getParameterTypes()[0];
            if (t == Long.class) {
                setId.invoke(entity, ((Number) id).longValue());
            } else if (t == Integer.class) {
                setId.invoke(entity, ((Number) id).intValue());
            } else {
                setId.invoke(entity, id);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 逐字段拷贝非空值（同 NOT_NULL 更新策略），跳过 serialVersionUID */
    private static void copyNotNull(Object src, Object dst) {
        Class<?> c = src.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object v = f.get(src);
                    if (v != null) {
                        f.set(dst, v);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            c = c.getSuperclass();
        }
    }

    private static Object readColumn(Object row, String column) {
        String fieldName = snakeToCamel(column);
        Class<?> c = row.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(row);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static String snakeToCamel(String s) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : s.toLowerCase().toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static List<Object> listOf(Object... xs) {
        return new ArrayList<>(Arrays.asList(xs));
    }
}
