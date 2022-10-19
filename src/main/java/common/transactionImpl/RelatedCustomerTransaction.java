package common.transactionImpl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import common.Transaction;

import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

public class RelatedCustomerTransaction extends Transaction {
    int C_W_ID;
    int C_D_ID;
    int C_ID;

    protected void YSQLExecute(Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            stmt.executeQuery(String.format("with customer_orderline as(" +
                    "select " +
                    "t2.O_C_ID, " +
                    "t1.OL_W_ID, t1.OL_D_ID, t1.OL_O_ID, t1.OL_I_ID " +
                    "from orderLine t1 " +
                    "left join orders t2 " +
                    "on t1.OL_W_ID=t2.O_W_ID " +
                    "AND t1.OL_D_ID=t2.O_D_ID " +
                    "AND t1.OL_O_ID=t2.O_ID), " +
                    "target_orderline as(" +
                    "select " +
                    "* from customer_orderline " +
                    "where OL_W_ID=%d AND OL_D_ID=%d AND O_C_ID=%d), " +
                    "other_orderline as(" +
                    "select " +
                    "* from customer_orderline " +
                    "where OL_W_ID!=%d) " +
                    "select " +
                    "target_w_id,target_d_id,target_c_id, " +
                    "output_w_id,output_d_id,output_c_id " +
                    "from (" +
                    "select " +
                    "t1.OL_W_ID as target_w_id,t1.OL_D_ID as target_d_id,t1.O_C_ID as target_c_id, " +
                    "t2.OL_W_ID as output_w_id,t2.OL_D_ID as output_d_id,t2.O_C_ID as output_c_id, " +
                    "count(*) as common_cnt " +
                    "from " +
                    "target_orderline as t1 " +
                    "left join other_orderline as t2 " +
                    "on t1.OL_I_ID=t2.OL_I_ID " +
                    "group by t1.OL_W_ID, t1.OL_D_ID, t1.O_C_ID, " +
                    "t2.OL_W_ID, t2.OL_D_ID, t2.O_C_ID " +
                    ")a " +
                    "where a.common_cnt>=2", C_W_ID, C_D_ID, C_ID, C_W_ID));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void execute(CqlSession session) {
        HashMap<List<Integer>, Integer> outputLine = new HashMap<List<Integer>, Integer>();
        SimpleStatement stmt = SimpleStatement.newInstance(String.format("select " +
                "O_W_ID, " +
                "O_D_ID, " +
                "O_ID " +
                "from order " +
                "where O_W_ID=%d AND O_D_ID=%d AND O_C_ID=%d " +
                "allow filtering", C_W_ID, C_D_ID, C_ID));
        com.datastax.oss.driver.api.core.cql.ResultSet rs = session.execute(stmt);
        for (Row row : rs) {
            StringBuilder itemList = new StringBuilder("(");
            stmt = SimpleStatement.newInstance(String.format("select " +
                    "OL_I_ID " +
                    "from orderline " +
                    "where OL_W_ID=%d " +
                    "and OL_D_ID=%d" +
                    "and OL_O_ID=%d " +
                    "tallow filtering", row.getInt(0), row.getInt(1), row.getInt(2)));
            com.datastax.oss.driver.api.core.cql.ResultSet newRs = session.execute(stmt);
            int count_item = 0;
            for (Row newRow : newRs) {
                if (count_item != 0) {
                    itemList.append(",");
                }
                itemList.append(String.valueOf(newRow.getInt("0")));
                count_item ++;
            }
            itemList.append(")");

            stmt = SimpleStatement.newInstance(String.format("select \n" +
                    "CI_C_ID, " +
                    "CI_W_ID, " +
                    "CI_D_ID, " +
                    "CI_O_ID, " +
                    "CI_I_ID " +
                    "from customer_item " +
                    "where CI_I_ID in %s " +
                    "and CI_W_ID!=%d " +
                    "allow filtering", itemList, C_W_ID));

        }
        // 存前三个ID作为key和对应出现item次数作为value
        com.datastax.oss.driver.api.core.cql.ResultSet outRs = session.execute(stmt);
        for (Row finalRs : outRs) {
            if (!Objects.equals(String.valueOf(finalRs.getInt("CI_W_ID")), String.valueOf(C_W_ID))) {
                List<Integer> order_info = Arrays.asList(finalRs.getInt("CI_W_ID"), finalRs.getInt("CI_D_ID"), finalRs.getInt("CI_C_ID"));
                boolean flag = outputLine.containsKey(order_info);
                if (flag) {
                    outputLine.put(order_info, outputLine.get(order_info)+1);
                }else {
                    outputLine.put(order_info, 1);
                }
            }
        }
        // 拿到了outputLine作为一个以List为key，MutableInteger为value的hashMap，后面对这个解析输出即可
    }

    public int getC_W_ID() {
        return C_W_ID;
    }

    public void setC_W_ID(int c_w_ID) {
        C_W_ID = c_w_ID;
    }

    public int getC_D_ID() {
        return C_D_ID;
    }

    public void setC_D_ID(int c_d_ID) {
        C_D_ID = c_d_ID;
    }

    public int getC_ID() {
        return C_ID;
    }

    public void setC_ID(int c_ID) {
        C_ID = c_ID;
    }
}
