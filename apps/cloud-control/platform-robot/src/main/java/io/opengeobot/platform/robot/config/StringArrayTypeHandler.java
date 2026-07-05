/*
 * Function: MyBatis TypeHandler for PostgreSQL text[] columns mapped to List<String>
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges {@code List<String>} entity fields and PostgreSQL {@code text[]}
 * columns. On write the list is converted to a SQL array via
 * {@link Connection#createArrayOf}; on read the array is unwrapped back into a
 * list. Used for {@code ota.release_campaign.target_robot_ids} and
 * {@code memory.failure_case.similar_case_ids}.
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        String[] array = parameter.toArray(new String[0]);
        Array sqlArray = ps.getConnection().createArrayOf("text", array);
        ps.setArray(i, sqlArray);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromArray(rs.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromArray(rs.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromArray(cs.getArray(columnIndex));
    }

    private List<String> fromArray(Array array) throws SQLException {
        if (array == null) {
            return new ArrayList<>();
        }
        Object raw = array.getArray();
        if (raw instanceof Object[] elements) {
            List<String> result = new ArrayList<>(elements.length);
            for (Object element : elements) {
                result.add(element == null ? null : element.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }
}
