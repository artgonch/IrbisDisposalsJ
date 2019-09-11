package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;

/**
 * Created by artem on 29.03.2018.
 */
public class VSPProcessingThread extends Thread {
    /** */
    private static final String PROGRESS_MESSAGE = "Обработано %d из %d";

    private List<String> barcodeList;
    private IrbisClient64 client;
    private ProgressDialogCallback callback;
    private String invNumPref;
    private String mhrNew = "";
    private String actNum = "";

    /**
     *
     * @param barcodeList
     * @param client
     * @param callback
     * @param invNumPref
     */
    public VSPProcessingThread(List<String> barcodeList,
                               IrbisClient64 client,
                               ProgressDialogCallback callback,
                               String invNumPref,
                               String mhrNew,
                               String actNum) {
        this.barcodeList = barcodeList;
        this.callback = callback;
        this.client = client;
        this.invNumPref = invNumPref;
        this.mhrNew = mhrNew;
        this.actNum = actNum;
    } // VSPProcessingThread

    public void run() {
        int n = barcodeList.size();
        final String sCurrDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        callback.init(String.format(PROGRESS_MESSAGE, 0, n), 0, n);

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
                    IrbisSubField64 sfA = f910.getSubFieldList().get('A');
                    if (sfH != null && sfH.getText().equalsIgnoreCase(barcode) || sfB != null && sfB.getText().equalsIgnoreCase(barcode)) {
                        if (!sfA.getText().equals("6")) {
                            currField = f910;
                            break;
                        }
                    } // if
                } // for (IrbisClient64 f910 : f910list)

                if (currField == null) {
                    continue;
                }

                // Поменять место хранения
                IrbisSubField64 sfD = currField.getSubFieldList().get('D');
                if (sfD == null) {
                    continue;
                }

                String mhrOld = sfD.getText();

                sfD.setText(mhrNew);

                // Добавить (или изменить) подполе G - дата модификации поля.
                IrbisSubField64 sfG = currField.getSubFieldList().get('G');
                if (sfG == null) {
                    currField.getSubFieldList().add('G', sCurrDate);
                }
                else {
                    sfG.setText(sCurrDate);
                } // else

                IrbisField64 f940 = new IrbisField64();
                f940.setTag("940");
                Iterator<IrbisSubField64> f910iter = currField.getSubFieldList().getSubFields().iterator();
                while (f910iter.hasNext()) {
                    IrbisSubField64 sf = f910iter.next();
                    f940.getSubFieldList().add(sf.getMarker(), sf.getText());
                } // while (f910iter.hasNext())
                f940.getSubFieldList().add('M', mhrNew);
                f940.getSubFieldList().add('W', actNum);
                f940.getSubFieldList().get('A').setText("6");
                f940.getSubFieldList().get('D').setText(mhrOld);
                record.addField(f940);
                // Сохранение записи
                client.writeRecord(record, false, true);
                callback.progress(i + 1, String.format(PROGRESS_MESSAGE, i + 1, n));
            } // for (int i = 0; i < n; i++)
        } // try
        catch (IrbisClient64Exception ex) {
            callback.error(ex);
        } // catch
        finally {
            callback.close();
        } // finally
    } // run
} // VSPProcessingThread
