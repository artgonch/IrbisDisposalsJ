package org.lplib.artem.irbisdisposalsj;

import javax.swing.*;
import java.awt.*;

/**
 * Created by artem on 14.02.2018.
 */
public class DocGeneratorPorgressDialog extends JDialog {
    /** */
    private JProgressBar progressBar = new JProgressBar();

    /** */
    private JLabel lblDescription = new JLabel();

    /**
     *
     */
    public DocGeneratorPorgressDialog(JFrame owner, String title) {
        super(owner, title, true);
        setSize(200, 100);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        add(lblDescription, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
        lblDescription.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        progressBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    } // DocGeneratorPorgressDialog

    /**
     *
     * @param min
     * @param max
     */
    public void initProgress(int min, int max, String text) {
        progressBar.setMinimum(min);
        progressBar.setMaximum(max);
        lblDescription.setText(text);
    }

    /**
     *
     * @param position
     * @param text
     */
    public void setProgress(int position, String text) {
        progressBar.setValue(position);
        lblDescription.setText(text);
        progressBar.updateUI();
    }
} // DocGeneratorPorgressDialog
