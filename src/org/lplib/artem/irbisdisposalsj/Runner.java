package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;
import org.artem.srg4j.RtfDocument;
import org.artem.srg4j.RtfTable;
import org.artem.srg4j.RtfTableCell;
import org.artem.srg4j.RtfTableRow;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;

/**
 * Created by artem on 12.02.2018.
 */
public class Runner extends JFrame {
    /** */
    private JTextPane tpBriefDescr = null;

    /** */
    private JButton btnGetDocumentForVSP = new JButton("Получить документ");

    /** */
    private JButton btnLoadInvNoList = new JButton("Загрузить список инвентарных номеров");

    private String host = "127.0.0.1";
    private int port = 6666;
    private String username = "1";
    private String password = "1";
    private String dbnam = "IBIS";
    private String briefFormatName = "brief";
    private String invNumPref = "IN=";
    private String invNumDispPref = "INS=";
    private int timeOutMs = 5000;
    private String sourceDefaultDir = ".";
    private String outputDefaultDir = ".";

    /** */
    private java.util.List<String> barcodeList = Collections.EMPTY_LIST;

    /** Программа выполняет работу в фоновом потоке */
    private boolean working = false;

    /**
     *
     * @throws Exception
     */
    private void loadConfig() throws Exception {
        BufferedReader in = null;

        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream("params.ini"), "windows-1251"));

            String s;

            while ((s = in.readLine()) != null) {
                int idx = s.indexOf('=');
                if (idx == -1) {
                    continue;
                }

                String name = s.substring(0, idx);
                String value = s.substring(idx + 1);

                if ("host".equals(name)) {
                    host = value;
                } else if ("port".equals(name)) {
                    port = Integer.valueOf(value);
                } else if ("username".equals(name)) {
                    username = value;
                } else if ("password".equals(name)) {
                    password = value;
                } else if ("dbnam".equals(name)) {
                    dbnam = value;
                } else if ("format-file-name".equals(name)) {
                    briefFormatName = value;
                } else if ("inv-num-pref".equals(name)) {
                    invNumPref = value;
                } else if ("inv-num-disp-pref".equals(name)) {
                    invNumDispPref = value;
                } else if ("time-out".equals(name)) {
                    timeOutMs = Integer.valueOf(value);
                } else if ("source-degault-dir".equals(name)) {
                    sourceDefaultDir = value;
                } else if ("output-default-dir".equals(name)) {
                    outputDefaultDir = value;
                } // else
            } // while ((s = in.readLine()) != null)
        } // try
        finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        } // finally
    } // loadConfig

    public Runner() {
        super("Списание и передача экземпляров");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        try {
            loadConfig();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка чтения конфигурации. Причина: %s", e.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(-1);
        }

        setSize(500, 600);
        setLocationRelativeTo(null);

        Container contentPane = this.getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));

        // Табоица-список загруженных инв. ноиеров
        final JTable tblInvNumList = new JTable(new BarCodeListTableModel());
        JScrollPane invNumListScrollPane = new JScrollPane(tblInvNumList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        invNumListScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        invNumListScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        invNumListScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        contentPane.add(invNumListScrollPane);

        // Текстовое поле краткого описания издания
        tpBriefDescr = new JTextPane();
        JScrollPane briefDescrScrollPane = new JScrollPane(tpBriefDescr, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        briefDescrScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        briefDescrScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 150));
        briefDescrScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        contentPane.add(briefDescrScrollPane);

        // Табулированное поле с режимами работы программы (списание/ВСП)
        JTabbedPane tabbedPaneAppMode = new JTabbedPane(SwingConstants.TOP);
        tabbedPaneAppMode.setPreferredSize(new Dimension(Integer.MAX_VALUE, 130));
        tabbedPaneAppMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        tabbedPaneAppMode.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        JPanel pnlVSPtab = new JPanel(new BorderLayout(5, 5));
        JPanel pnlVSPFields = new JPanel(new GridLayout(3, 2, 5, 5));
        pnlVSPFields.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlVSPFields.add(new JLabel("№ акта передачи"));
        final JTextField tfVSPActNum = new JTextField();
        pnlVSPFields.add(tfVSPActNum);
        pnlVSPFields.add(new JLabel("МХР новое"));
        final JTextField tfVSPMhrNew = new JTextField();
        pnlVSPFields.add(tfVSPMhrNew);
        pnlVSPFields.add(new JLabel("МХР текущее"));
        final JTextField tfMhrCurr = new JTextField();
        pnlVSPFields.add(tfMhrCurr);
        pnlVSPtab.add(pnlVSPFields, BorderLayout.CENTER);
        JButton btnMakeVSP = new JButton("Выполнить перемещение");
        btnMakeVSP.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlVSPtab.add(btnMakeVSP, BorderLayout.SOUTH);
        // *****************************
        // Панель режима "списание".
        JPanel pnlDoisposalTab = new JPanel(new BorderLayout(5, 5));
        JPanel pnlDisposalsFields = new JPanel(new GridLayout(1, 2, 5, 5));
        pnlDisposalsFields.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlDisposalsFields.add(new JLabel("№ акта списания"));
        final JTextField tfDisposalsActNum = new JTextField();
        pnlDisposalsFields.add(tfDisposalsActNum);
        JButton btnMakeDisposal = new JButton("Выполнить списание");
        btnMakeDisposal.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlDoisposalTab.add(pnlDisposalsFields);
        pnlDoisposalTab.add(btnMakeDisposal, BorderLayout.SOUTH);
        // *****************************
        // Панель "Смен статусов экз-ров".
        JPanel pnlChangeStatusTab = new JPanel((new BorderLayout(5, 5)));
        JPanel pnlChngStatFields = new JPanel(new GridLayout(1, 2, 5, 5));
        pnlChngStatFields.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlChngStatFields.add(new JLabel("Новый статус"));
        final JTextField tfNewStatus = new JTextField();
        pnlChngStatFields.add(tfNewStatus);
        final JButton btnChangeStstus = new JButton("Сменить статус");
        btnChangeStstus.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlChangeStatusTab.add(pnlChngStatFields);
        pnlChangeStatusTab.add(btnChangeStstus, BorderLayout.SOUTH);
        // *****************************
        tabbedPaneAppMode.addTab("ВСП", null, pnlVSPtab);
        tabbedPaneAppMode.addTab("Списание", null, pnlDoisposalTab);
        tabbedPaneAppMode.addTab("Смена статусов экз.", null, pnlChangeStatusTab);
        contentPane.add(tabbedPaneAppMode);

        // Кнока загрузки списка ШК
        JPanel pnlButtons = new JPanel();
        pnlButtons.setLayout(new BoxLayout(pnlButtons, BoxLayout.PAGE_AXIS));
        pnlButtons.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        pnlButtons.setPreferredSize(new Dimension(Integer.MAX_VALUE, 90));
        pnlButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        pnlButtons.add(btnLoadInvNoList);
        pnlButtons.add(Box.createVerticalStrut(5));
        pnlButtons.add(btnGetDocumentForVSP);

        contentPane.add(pnlButtons);

        final JPopupMenu barcodeListPopupMenu = new JPopupMenu();
        JMenuItem barcodeListPopupCopyItem = new JMenuItem("Копировать");
        barcodeListPopupMenu.add(barcodeListPopupCopyItem);

        // Обработчики событий
        // Надатие правой кнопки мыши на списке инв. номеров
        barcodeListPopupCopyItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int sel = tblInvNumList.getSelectedRow();
                if (sel == -1) {
                    return;
                }

                String bc = barcodeList.get(sel);
                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection stringSelection = new StringSelection(bc);
                clpbrd.setContents(stringSelection, null);
            }
        });

        // Нажатие кнопки чтения списка инвентарных номеров
        btnLoadInvNoList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File currDir = new File(sourceDefaultDir);
                if (!currDir.isDirectory()) {
                    currDir = new File(".");
                }
                JFileChooser fc = new JFileChooser(currDir);
                fc.setFileFilter(new FileNameExtensionFilter("Текстовые файлы", "txt"));
                if (fc.showDialog(Runner.this, "Открыть") != JFileChooser.APPROVE_OPTION) {
                    return;
                }

                barcodeList = new ArrayList<>();

                BufferedReader in = null;

                // Подключиться к серверу ИРБИС
                final IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);

                try {
                    in = new BufferedReader(new InputStreamReader(new FileInputStream(fc.getSelectedFile()), "windows-1251"));
                    String s;
                    while ((s = in.readLine()) != null) {
                        barcodeList.add(s);
                    }
                } // try
                catch (IOException e1) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка чтения списка ШК. Причина: %s", e1.getMessage()));
                    return;
                }

                try {
                    client.setTimeOutMs(5000);
                    client.connect();

                    final BarCodeListTableModel tableModel = new BarCodeListTableModel();

                    tblInvNumList.setModel(tableModel);

                    LoadBarcodeListThread thread = new LoadBarcodeListThread(client, new LoadBarcodeListThreadCallBack() {
                        @Override
                        public void start() {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    btnGetDocumentForVSP.setEnabled(false);
                                    btnLoadInvNoList.setEnabled(false);
                                }
                            });
                        }

                        @Override
                        public void stop() {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    btnGetDocumentForVSP.setEnabled(true);
                                    btnLoadInvNoList.setEnabled(true);
                                    client.disconnect();
                                    JOptionPane.showMessageDialog(Runner.this, "Загрузка списка звершена", "Загрузка", JOptionPane.INFORMATION_MESSAGE);
                                }
                            });
                        }

                        @Override
                        public void addElement(String barcode, String status) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        tableModel.add(barcode, status);
                                        tblInvNumList.updateUI();
                                    }
                                });
                            } catch (InvocationTargetException e) {

                            } catch (InterruptedException e) {

                            }
                        }

                        @Override
                        public boolean error(Exception exception) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка работы с сервером. Причина: %s", exception.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                                    }
                                });
                            } // try
                            catch (Exception e) {
                                return true;
                            }
                            return true;
                        } // error
                    }, barcodeList, invNumPref, tfMhrCurr.getText());

                    thread.start();
                } // try
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при получении данных от сервера. причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception e1) {
                        }
                    } // if
                } // finally
            } // actionPerformed
        });

        // Выбор строки таблицы инвентарных номеров
        tblInvNumList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);

                try {
                    client.setTimeOutMs(5000);
                    client.connect();

                    int sel = tblInvNumList.getSelectedRow();
                    if (sel == -1) {
                        return;
                    }
                    String bc = barcodeList.get(sel);
                    List<String> result = client.search(String.format("\"%s%s\"", invNumPref, bc), briefFormatName);
                    tpBriefDescr.setText(result.isEmpty() ? "Издание не найдено" : result.get(0));
                } // try
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при получении данных от сервера. Причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                } finally {
                    client.disconnect();
                }
            }
        });

        tblInvNumList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    barcodeListPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // Получение документа ВСП по списку ШК
        btnGetDocumentForVSP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (barcodeList.isEmpty()) {
                    JOptionPane.showMessageDialog(Runner.this, "Список инвентарных номеров пуст", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                final IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);

                final RtfDocument doc = new RtfDocument();

                try {
                    client.setTimeOutMs(5000);
                    client.connect();

                    final DocGeneratorPorgressDialog dialog = new DocGeneratorPorgressDialog(Runner.this, "Генерация документа");
                    final ProgressDialogCallback callback = new ProgressDialogCallback() {
                        @Override
                        public void init(String text, int min, int max) {
//                            try {
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        btnGetDocumentForVSP.setEnabled(true);
                                        btnLoadInvNoList.setEnabled(true);
                                        dialog.initProgress(0, barcodeList.size(), "Обработано %d из %d");
                                        dialog.setVisible(true);
                                    }
                                });
//                            } // try
//                            catch (InterruptedException e) {}
//                            catch (InvocationTargetException e) {
//                                System.out.println(e.getMessage());
//                            }
                        } // init

                        @Override
                        public void progress(int step, String text) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setProgress(step, text);
                                }
                            });
                        }

                        @Override
                        public void error(Exception ex) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка выполнения операции. Причина: %s", ex.getMessage()));
                                        dialog.setVisible(false);
                                    }
                                });
                            }
                            catch (InterruptedException e) {}
                            catch (InvocationTargetException e) {}
                        } // error

                        @Override
                        public void close() {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.disconnect();

                                        dialog.setVisible(false);

                                        btnGetDocumentForVSP.setEnabled(true);
                                        btnLoadInvNoList.setEnabled(true);

                                        // Запросить имя файла и сохранить документ
                                        File currDir = new File(outputDefaultDir);
                                        if (!currDir.isDirectory()) {
                                            currDir = new File(".");
                                        }
                                        JFileChooser fc = new JFileChooser(currDir);
                                        final String filenameExt = "rtf";
                                        final String filenameExtDot = ".RTF";
                                        fc.setFileFilter(new FileNameExtensionFilter("Reach Text Format", filenameExt));

                                        if (fc.showSaveDialog(Runner.this) != JFileChooser.APPROVE_OPTION) {
                                            return;
                                        }

                                        try {
                                            File selectedFile = fc.getSelectedFile();
                                            String s = selectedFile.getPath();
                                            if (!s.toUpperCase().endsWith(filenameExtDot)) {
                                                s += filenameExtDot;
                                                selectedFile = new File(s);
                                            }
                                            doc.save(selectedFile);

                                            Desktop.getDesktop().open(selectedFile);
                                        } catch (IOException ex) {
                                            JOptionPane.showMessageDialog(Runner.this, String.format("Ошиька сохранения файла. Причина: %s", ex.getMessage()));
                                        }
                                    } // run
                                });
                            } // try
                            catch (InterruptedException e) {}
                            catch (InvocationTargetException e) {}
                        } // close
                    };

                    RawDocumentGeneratorThread thread = new RawDocumentGeneratorThread(barcodeList, client, callback, doc, invNumPref);
                    thread.start();
                } // try
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при подключении к серверу. Причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Выполнение смены мест хранения (ВСП)
        btnMakeVSP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tfVSPActNum.getText().equals("")) {
                    JOptionPane.showMessageDialog(Runner.this, "Не указан номер акта передачи", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (tfVSPMhrNew.getText().equals("")) {
                    JOptionPane.showMessageDialog(Runner.this, "Не указано новое место хранения экз-ра", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    final IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);
                    client.setTimeOutMs(timeOutMs);
                    client.connect();

                    final DocGeneratorPorgressDialog dialog = new DocGeneratorPorgressDialog(Runner.this, "ВСП");
                    ProgressDialogCallback callback = new ProgressDialogCallback() {
                        @Override
                        public void init(String text, int min, int max) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    btnGetDocumentForVSP.setEnabled(true);
                                    btnLoadInvNoList.setEnabled(true);
                                    dialog.initProgress(0, barcodeList.size(), "Обработано %d из %d");
                                    dialog.setVisible(true);
                                }
                            });
                        }

                        @Override
                        public void progress(int value, String text) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setProgress(value, text);
                                }
                            });
                        }

                        @Override
                        public void error(Exception ex) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка выполнения операции. Причина: %s", ex.getMessage()));
                                        dialog.setVisible(false);
                                    }
                                });
                            } catch (InterruptedException e) {
                            } catch (InvocationTargetException e) {
                            }
                        }

                        @Override
                        public void close() {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.disconnect();

                                        dialog.setVisible(false);

                                        btnGetDocumentForVSP.setEnabled(true);
                                        btnLoadInvNoList.setEnabled(true);

                                        JOptionPane.showMessageDialog(Runner.this, "Процесс хавершен", "ВСП", JOptionPane.INFORMATION_MESSAGE);
                                    } // run
                                });
                            } // try
                            catch (InterruptedException ex){}
                            catch (InvocationTargetException ex){}
                        } // close
                    };
                    VSPProcessingThread vspThread = new VSPProcessingThread(barcodeList, client, callback, invNumPref, tfVSPMhrNew.getText(), tfVSPActNum.getText());
                    vspThread.start();
                }
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при подключении к серверу. Причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } // actionPerformed
        });

        btnMakeDisposal.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tfDisposalsActNum.getText().equals("")) {
                    JOptionPane.showMessageDialog(Runner.this, "Не указан номер акта списания", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    final IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);
                    client.setTimeOutMs(timeOutMs);
                    client.connect();

                    final DocGeneratorPorgressDialog dialog = new DocGeneratorPorgressDialog(Runner.this, "Списание");
                    ProgressDialogCallback callback = new ProgressDialogCallback() {
                        @Override
                        public void init(String text, int min, int max) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    btnGetDocumentForVSP.setEnabled(true);
                                    btnLoadInvNoList.setEnabled(true);
                                    dialog.initProgress(0, barcodeList.size(), "Обработано %d из %d");
                                    dialog.setVisible(true);
                                }
                            });
                        }

                        @Override
                        public void progress(int value, String text) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setProgress(value, text);
                                }
                            });
                        }

                        @Override
                        public void error(Exception ex) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка выполнения операции. Причина: %s", ex.getMessage()));
                                        dialog.setVisible(false);
                                    }
                                });
                            } catch (InterruptedException e) {
                            } catch (InvocationTargetException e) {
                            }
                        }

                        @Override
                        public void close() {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.disconnect();

                                        dialog.setVisible(false);

                                        btnGetDocumentForVSP.setEnabled(true);
                                        btnLoadInvNoList.setEnabled(true);

                                        JOptionPane.showMessageDialog(Runner.this, "Процесс хавершен", "Списание", JOptionPane.INFORMATION_MESSAGE);
                                    } // run
                                });
                            } // try
                            catch (InterruptedException ex){}
                            catch (InvocationTargetException ex){}
                        } // close
                    };
                    DisposalProcessingThread disposalThread = new DisposalProcessingThread(barcodeList, client, callback, invNumPref, tfDisposalsActNum.getText());
                    disposalThread.start();
                } // try
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при подключении к серверу. Причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } // actionPerformed
        });

        // Обработчик события нажатия кнопки смены статуса экз-ров
        btnChangeStstus.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tfNewStatus.getText().length() == 0) {
                    JOptionPane.showMessageDialog(Runner.this, "Не указан статус экз-ра", "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    final IrbisClient64 client = new IrbisClient64(host, port, username, password, dbnam);
                    client.setTimeOutMs(timeOutMs);
                    client.connect();

                    final DocGeneratorPorgressDialog dialog = new DocGeneratorPorgressDialog(Runner.this, "Смена статуса экз.");
                    ProgressDialogCallback callback = new ProgressDialogCallback() {
                        @Override
                        public void init(String text, int min, int max) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    btnChangeStstus.setEnabled(true);
                                    btnLoadInvNoList.setEnabled(true);
                                    dialog.initProgress(0, barcodeList.size(), "Обработано %d из %d");
                                    dialog.setVisible(true);
                                }
                            });
                        }

                        @Override
                        public void progress(int value, String text) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setProgress(value, text);
                                }
                            });
                        }

                        @Override
                        public void error(Exception ex) {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка выполнения операции. Причина: %s", ex.getMessage()));
                                        dialog.setVisible(false);
                                    }
                                });
                            } catch (InterruptedException e) {
                            } catch (InvocationTargetException e) {
                            }
                        }

                        @Override
                        public void close() {
                            try {
                                SwingUtilities.invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.disconnect();

                                        dialog.setVisible(false);

                                        btnChangeStstus.setEnabled(true);
                                        btnLoadInvNoList.setEnabled(true);

                                        JOptionPane.showMessageDialog(Runner.this, "Процесс хавершен", "Смена статуса экз.", JOptionPane.INFORMATION_MESSAGE);
                                    } // run
                                });
                            } // try
                            catch (InterruptedException ex){}
                            catch (InvocationTargetException ex){}
                        }
                    };
                    ChangeStatusThread thread = new ChangeStatusThread(barcodeList, client, callback, tfNewStatus.getText(), invNumPref);
                    thread.start();
                } // try
                catch (IrbisClient64Exception ex) {
                    JOptionPane.showMessageDialog(Runner.this, String.format("Ошибка при подключении к серверу. Причина: %s", ex.getMessage()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } // actionPerformed
        });
    } // Runner()

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Runner r = new Runner();
                r.setVisible(true);
            }
        });
    }
}
