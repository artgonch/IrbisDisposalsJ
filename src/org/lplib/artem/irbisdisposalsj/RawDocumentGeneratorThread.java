package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;
import org.artem.srg4j.RtfDocument;
import org.artem.srg4j.RtfTable;
import org.artem.srg4j.RtfTableCell;
import org.artem.srg4j.RtfTableRow;

import java.util.*;

/**
 * Created by artem on 16.02.2018.
 * ?????????? ????????-?????? ?? ??? ?? ?????? ??????? ???. ???????.
 */
public class RawDocumentGeneratorThread extends Thread {
    private List<String> barcodeList;
    private IrbisClient64 client;
    private ProgressDialogCallback callback;
    private RtfDocument outDocument;
    private String invNumPref;

    /**
     * @param barcodeList
     * @param client
     * @param callback
     */
    public RawDocumentGeneratorThread(List<String> barcodeList,
                                      IrbisClient64 client,
                                      ProgressDialogCallback callback,
                                      RtfDocument outDocument,
                                      String invNumPref) {
        this.barcodeList = barcodeList;
        this.callback = callback;
        this.client = client;
        this.outDocument = outDocument;
        this.invNumPref = invNumPref;
    } // RawDocumentGeneratorThread

    /**
     *
     */
    public void run() {
        int n = barcodeList.size();
        final String progressMessage = "Обработано %d из %d";

        float totalSumm = 0.0f;

        callback.init(String.format(progressMessage, 0, n), 0, n);

        // ????????? ???????.
        RtfTable table = outDocument.createTable(100.0f, new float[]{10.0f, 15.0f, 47.0f, 10.0f, 8.8f, 10.0f,}, RtfTable.TABLE_ALIGMENT.CENTER);

        RtfTableRow header = table.createTableHeader();
        header.getCell(0).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("№№").bold();
        header.getCell(1).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("Инв. №").bold();
        header.getCell(2).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("Автор, заглавие").bold();
        header.getCell(3).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("Год").bold();
        header.getCell(4).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("Отдел").bold();
        header.getCell(5).borderAll().horizontalALigment(RtfTableCell.ALIGMENT.CENTER).verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).createChunk("Цена").bold();

        Map<String, IntegerWrapper> rznMap = new HashMap<String, IntegerWrapper>(15);

        List<String[]> tableData = new ArrayList<String[]>();

        try {
            for (int i = 0; i < n; i++) {
                if (isInterrupted()) {
                    return;
                } // if

                String barcode = barcodeList.get(i);

                List<Integer> result = client.search(String.format("\"%s%s\"", invNumPref, barcode));

                if (result.isEmpty()) {
                    continue;
                }

                IrbisRecord64 record = client.readRecord(result.get(0), false);

                IrbisField64 currField = null;

                List<IrbisField64> f910list = record.getFieldSet("910");
                for (IrbisField64 f910 : f910list) {
                    IrbisSubField64 sfH = f910.getSubFieldList().get('H');
                    IrbisSubField64 sfB = f910.getSubFieldList().get('B');
                    if (sfH != null && sfH.getText().equals(barcode) || sfB != null && sfB.getText().equals(barcode)) {
                        currField = f910;
                        break;
                    } // if
                } // for (IrbisClient64 f910 : f910list)

                if (currField == null) {
                    continue;
                }

                // ?????, ????????.
                String biblioInfo = "";
                String f461x = record.getData("461", 'X');
                String f461c = record.getData("461", 'C');
                String f200v = record.getData("200", 'V');
                String f200a = record.getData("200", 'A');
                String f700a = record.getData("700", 'A');
                String f700b = record.getData("700", 'B');
                String f215a = record.getData("215", 'A');
                String f215_1 = record.getData("215", '1');
                String rzn = record.getData("60");

                if (rzn != null) {
                    IntegerWrapper iw = rznMap.get(rzn);
                    if (iw == null) {
                        iw = new IntegerWrapper();
                        rznMap.put(rzn, iw);
                    }
                    iw.value++;
                } // if

                if (f461x != null) {
                    biblioInfo = f461x + ". ";
                }
                if (f461c != null) {
                    if (biblioInfo.length() > 0) {
                        biblioInfo += " ";
                    }
                    biblioInfo += f461c;
                }
                if (f200v != null) {
                    biblioInfo += "; " + f200v;

                    if (f700a != null) {
                        biblioInfo += ": ";
                    }
                }
                biblioInfo += (f700a != null ? f700a : "");
                if (f700b != null) {
                    biblioInfo += " " + f700b;
                }
                if (f200a != null) {
                    if (biblioInfo.length() > 0) {
                        biblioInfo += " ";
                    }
                    biblioInfo += f200a;
                }
//                if (f215a != null) {
//                    biblioInfo += ". - " + f215a + " " + (f215_1 != null ? f215_1 : "с.");
//                    biblioInfo += ". - " + f215a;
//                }
                String year = record.getData("210", 'D');
                String knowlageDept = record.getData("60");
                String price = record.getData("10", 'D');

                String[] dataLine = new String[6];
                // 1 - ?????????? ?
                dataLine[0] = String.valueOf(i + 1);
                // 2 - ?????-???
                dataLine[1] = currField.getSubFieldList().get('B') != null ? currField.getSubFieldList().get('B').getText() : currField.getSubFieldList().get('H').getText();
                // 3 - ?????, ????????
                dataLine[2] = biblioInfo;
                // 4 - ???
                dataLine[3] = year != null ? year : "";
                // 5 - ?????? ???????
                dataLine[4] = knowlageDept != null ? knowlageDept : "";
                // 6 - ????
//                final String sPrice = price != null ? price : currField.getSubFieldList().get('E') != null ? currField.getSubFieldList().get('E').getText() : "";
                final String sPrice = currField.getSubFieldList().get('E') != null ? currField.getSubFieldList().get('E').getText() : price != null ? price : "";
                dataLine[5] = sPrice;

                try {
                    totalSumm += Float.valueOf(sPrice);
                } catch (NumberFormatException e) {
                    totalSumm += 0.0f;
                }

                tableData.add(dataLine);

                callback.progress(i + 1, String.format(progressMessage, i + 1, n));
                // ***************************************
//                RtfTableRow row = table.createTableRow();
                // 1 - ?????????? ?
//                row.getCell(0).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(String.valueOf(i + 1));
                // 2 - ?????-???
//                row.getCell(1).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(barcode);
                // 3 - ?????, ????????
//                row.getCell(2).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.JUSTIFY).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(biblioInfo);
                // 4 - ???
//                row.getCell(3).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.JUSTIFY).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(year != null ? year : "");
                // 5 - ?????? ???????
//                row.getCell(4).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(knowlageDept != null ? knowlageDept : "");
                // 6 - ????
//                final String sPrice = price != null ? price : currField.getSubFieldList().get('E') != null ? currField.getSubFieldList().get('E').getText() : "";

//                row.getCell(5).borderAll().
//                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
//                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
//                        createChunk(sPrice);
//
//                try {
//                    totalSumm += Float.valueOf(sPrice);
//                }
//                catch (NumberFormatException e) {
//                    totalSumm += 0.0f;
//                }
//
//                callback.progress(i + 1, String.format(progressMessage, i + 1, n));
            } // for (int i = 0; i < n; i++)

            // ??????????
            Collections.sort(tableData, new Comparator<String[]>() {
                public int compare(String[] o1, String[] o2) {
                    return o1[2].compareTo(o2[2]);
                }
            });
            // ???????????????
            int m = tableData.size();
            for (int i = 0; i < m; i++) {
                tableData.get(i)[0] = String.valueOf(i + 1);
            } // for (int i = 0; i < m; i++)

            // ???????????? ??????? ? RTF-?????????
            for (String[] dataLine : tableData) {
                RtfTableRow row = table.createTableRow();
                // 1 - ?????????? ?
                row.getCell(0).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[0]);
                // 2 - ?????-???
                row.getCell(1).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[1]);
                // 3 - ?????, ????????
                row.getCell(2).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.JUSTIFY).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[2]);
                // 4 - ???
                row.getCell(3).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.JUSTIFY).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[3]);
                // 5 - ?????? ???????
                row.getCell(4).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[4]);
                // 6 - ????
                row.getCell(5).borderAll().
                        horizontalALigment(RtfTableCell.ALIGMENT.CENTER).
                        verticalAligment(RtfTableCell.VERT_ALIGMENT.CENTER).
                        createChunk(dataLine[5]);
            } // for (String[] dataLine : tableData)

            // ?????????? ??????? ????????????? ?? ???????
            outDocument.createParagraph().createChunk(String.format("Всего экз-ров %d на сумму %.2f ?.", n, totalSumm));

            // ?????????? ??????? ????????????? ?? ???????
            outDocument.createParagraph().createChunk("Распределение по отделам:").bold();

            final int size = rznMap.size();
            float[] rznColumns = new float[size];
            for (int i = 0; i < size; i++) {
                rznColumns[i] = 100.0f / size;
            } // for (int i = 0; i < size; i++)
            RtfTable rznTable = outDocument.createTable(100.0f, rznColumns, RtfTable.TABLE_ALIGMENT.CENTER);

            RtfTableRow row = rznTable.createTableRow();
            Iterator<String> keys = rznMap.keySet().iterator();
            for (int i = 0; i < size; i++) {
                String key = keys.next();
                row.getCell(i).borderAll().createChunk(key);
            } // for (int i = 0; i < size; i++)
            row = rznTable.createTableRow();
            keys = rznMap.keySet().iterator();
            for (int i = 0; i < size; i++) {
                String key = keys.next();
                row.getCell(i).borderAll().createChunk(String.valueOf(rznMap.get(key).value));
            } // for (int i = 0; i < size; i++)
        } // try
        catch (IrbisClient64Exception ex) {
            callback.error(ex);
        } finally {
            callback.close();
        }
    } // run
} // RawDocumentGeneratorThread
