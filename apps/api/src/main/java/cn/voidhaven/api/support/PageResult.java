package cn.voidhaven.api.support;

import java.util.List;

public record PageResult<T>(List<T> items, String nextCursor) {
    public static <T> PageResult<T> from(List<T> values, int pageSize, java.util.function.Function<T, String> cursor) {
        boolean hasMore = values.size() > pageSize;
        List<T> page = hasMore ? List.copyOf(values.subList(0, pageSize)) : List.copyOf(values);
        return new PageResult<>(page, hasMore && !page.isEmpty() ? cursor.apply(page.get(page.size() - 1)) : null);
    }
}
