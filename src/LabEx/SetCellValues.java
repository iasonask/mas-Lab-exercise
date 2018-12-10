/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package LabEx;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

public class SetCellValues extends JFrame implements ActionListener {

    JTable table;
    JTextArea textArea;
    public JButton playButton;
    public JButton resetButton;
    private JTextField display;
    String command = "";

    public SetCellValues() {

        JFrame frame = new JFrame("Showing Negotiation results");
        //panel to show the feeder status
        JPanel panel = new JPanel();
        String data[][] = new String[2][4];
        for (int i = 0; i < 4; i++) {
            data[0][i] = "";
        }
        data[1][0] = "Waiting for results....";
        String col[] = {"DSO", "Storage(SOC)", "PV", "Load"};
        DefaultTableModel model = new DefaultTableModel(data, col);
        table = new JTable(model);

        JTableHeader header = table.getTableHeader();
        header.setBackground(Color.green);
        JScrollPane pane = new JScrollPane(table);
        
        panel.add(pane);
        //frame.add(panel, BorderLayout.WEST);

        //buttons
        playButton = new JButton("PLaY");
        playButton.setSize(60, 30);
        resetButton = new JButton("Reset");
        resetButton.setSize(20, 10);
        frame.add(playButton, BorderLayout.SOUTH);
        frame.add(resetButton, BorderLayout.LINE_END);
        frame.add(panel);

       
        frame.setSize(540, 200);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    }

    public void SetAgentData(Object obj, int row_index, int col_index) {
        table.getModel().setValueAt(obj, row_index, col_index);
        //System.out.println("Value is added");

    }

    public String passCommand() throws InterruptedException {

        while (command.isEmpty()) {

        }
        return command;
    }

    public void actionPerformed(ActionEvent e) {
        JButton source = (JButton) e.getSource();
        display.replaceSelection(source.getActionCommand());
    }

    public void negotiationCompleted() {

        table.getModel().setValueAt("Updated!", 1, 0);
//        try {
//            Thread.sleep(1500);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(SetCellValues.class.getName()).log(Level.SEVERE, null, ex);
//        }
        table.getModel().setValueAt("Waiting....", 1, 0);

    }

}
