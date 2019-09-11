package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;

import java.util.List;

/**
 * Created by artem on 14.02.2018.
 */
public class LoadBarcodeListThread extends Thread {
    /** */
    private IrbisClient64 client;

    /** */
    private LoadBarcodeListThreadCallBack callback;

    /** */
    private List<String> barcodeList;

    /** */
    private String invNumPref;

    /** */
    private String currentMhr = "";

    /**
     * @param client
     * @param callBack
     */
    public LoadBarcodeListThread(IrbisClient64 client, LoadBarcodeListThreadCallBack callBack, List<String> barcodeList, String invNumPref, String currentMhr) {
        this.client = client;
        this.callback = callBack;
        this.barcodeList = barcodeList;
        this.invNumPref = invNumPref;
        this.currentMhr = currentMhr;
    } // LoadBarcodeListThread

    @Override
    public void run() {
        callback.start();

        try {

            for (String barcode : barcodeList) {
                try {
                    if (isInterrupted()) {
                        return;
                    }

                    if (barcode.equals("")) {
                        continue;
                    }

                    List<Integer> result = client.search(String.format("\"%s%s\"", invNumPref, barcode));

                    String status = "";

                    if (result.isEmpty()) {
                        status = "Не найден";
                    } else if (result.size() > 1) {
                        status = "Дубликат";
                    } else {
                        // Проверить соответствие места хранения экз-ра заданному
                        if (!"".equals(currentMhr)) {
                            IrbisRecord64 rec = client.readRecord(result.get(0), false);
                            List<IrbisField64> f910list = rec.getFieldSet("910");
                            for (IrbisField64 f910 : f910list) {
                                IrbisSubField64 sfB = f910.getSubFieldList().get('B');
                                IrbisSubField64 sfH = f910.getSubFieldList().get('H');
                                IrbisSubField64 sfD = f910.getSubFieldList().get('D');
                                if (sfB != null && sfB.getText().equals(barcode) || sfH != null && sfH.getText().equals(barcode)) {
                                    if (sfD == null || !sfD.getText().toUpperCase().equals(currentMhr)) {
                                        status = "Неверное м. хранения";
                                    }
                                } // if (sfB != null && sfB.getText().equals(barcode) || sfH != null && sfH.getText().equals(barcode))
                            } // for (IrbisField64 f910 : f910list)
                        } // if
                    } // else

                    callback.addElement(barcode, status);
                } // try
                catch (IrbisClient64Exception e) {
                    // Если метод error вернул false, то прервать поток, иначе продолжить несмотря на ошибку.
                    if (!callback.error(e)) {
                        return;
                    }
                } // catch
            } // for (String barcode : barcodeList)
        } // try
        finally {
            callback.stop();
        } // finally
    } // run
} // LoadBarcodeListThread
