package org.lplib.artem.irbisdisposalsj;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by artem on 12.02.2018.
 */
public class BarCodeListTableModel extends AbstractTableModel {
    private List<String[]> dataList = new ArrayList<>();

    @Override
    public int getRowCount() {
        return dataList.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        String[] data  = dataList.get(rowIndex);
        return data[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public String getColumnName(int column) {
        if (column == 0) {
            return "Инв. номер/ШК";
        }
        else {
            return "Примечание";
        }
    }

    public void add(String barcode, String statis) {
        dataList.add(new String[]{barcode, statis});
    }
} // BarCodeListTableModel
