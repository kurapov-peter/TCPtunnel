package com.gui;

import com.config.loader.ConfigSaver;
import com.server.IServer;
import com.server.portpool.ManageablePortPool;
import com.server.status.IManageableStatus;
import com.server.status.IStatus;
import com.server.status.Status;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Gui implements IGui, Runnable {
    private final IServer server;
    private final ManageablePortPool pool;
    private final ConfigSaver configSaver;

    private JFrame mainFrame;
    private JPanel controlPanel;
    private JTree resourceTree;
    private JScrollPane scrollPane;
    private JTextArea logArea;
    private JTextPane logPane;

    // Selected port pair
    private Map<Integer, Integer> selectedPair = new HashMap<>();

    public Gui(IServer server, ManageablePortPool pool, ConfigSaver configSaver) {
        // Set instances
        this.server = server;
        this.pool = pool;
        this.configSaver = configSaver;

        prepareGUI();

    }

    private void prepareGUI() {
        mainFrame = new JFrame("Retranslator");
        mainFrame.setSize(420, 500);
        mainFrame.setLocation(400, 300);

        // Set closing action for main frame
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        // Initialize info tree
        updateRuleTree();
        updateRulesStatus();

        // Set tree listeners
        setResourceListeners();

        // Set renderer for rules tree
        resourceTree.setCellRenderer(new MyRenderer());

        // Set logger output
        setLoggerOutput();

        // Tell log pane to render html
        logPane.setContentType("text/html");

        // Add components to main frame
        mainFrame.add(controlPanel);
    }

    private void setResourceListeners() {

        // Listener for logging
        resourceTree.addTreeSelectionListener(e -> {
            // Erase logger focus
            selectedPair.clear();

            // Get rule
            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) resourceTree.getLastSelectedPathComponent();

            if (selectedNode.getParent() == selectedNode.getRoot()) {
                int portA = (int) ((Map.Entry) selectedNode.getUserObject()).getKey();
                int portB = (int) ((Map.Entry) selectedNode.getUserObject()).getValue();

                // Set logging for selected rule
                selectedPair.put(portA, portB);
            }
        });

        // Listener for selecting node on expand
        resourceTree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                resourceTree.setSelectionPath(event.getPath());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {/**/}
        });
    }

    private void setLoggerOutput() {
        Logger logger = Logger.getLogger(server.getClass().getName());
        Handler handler = new TextPaneComponentHandler(logPane);
        logger.addHandler(handler);
    }

    private void updateRulesStatus() {
        DefaultTreeModel model = (DefaultTreeModel) resourceTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        Enumeration en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) en.nextElement();

            // Rules are first generation children
            if (node.getParent() == root) {
                Map.Entry portPair = (Map.Entry) node.getUserObject();
                IStatus status = server.getStatus((int) portPair.getKey());

                // Server not running -> render form for status
                if (status == null) {
                    IManageableStatus emptyStatus = new Status(portPair);
                    attachStatus(node, emptyStatus);
                } else {
                    node.removeAllChildren();
                    attachStatus(node, status);
                }

            }
        }
    }

    private void attachStatus(DefaultMutableTreeNode node, IStatus status) {
        // Add state
        String connectionState = "Состояние: ";
        if (status.isConnected()) {
            connectionState += "Подключение активно";
        } else {
            connectionState += "Соединения нет";
        }

        DefaultMutableTreeNode connectionNode = new DefaultMutableTreeNode(connectionState);

        // Add received info
        DefaultMutableTreeNode receivedNode = new DefaultMutableTreeNode("Принято байт: "
                + status.getRecv());

        // Add sent info
        DefaultMutableTreeNode sentNode = new DefaultMutableTreeNode("Отправлено байт: "
                + status.getSent());

        // Add last connection date
        LocalDateTime lastConnected = status.getLastTimeConnected();
        String connected = (lastConnected == null) ? "" : lastConnected.toString();
        DefaultMutableTreeNode dateNode = new DefaultMutableTreeNode("Последнее подключение: "
                + connected);

        node.add(connectionNode);
        node.add(receivedNode);
        node.add(sentNode);
        node.add(dateNode);
    }

    private void updateRuleTree() {
        // Get all rules
        Map<Integer, Integer> portRules = pool.getRules();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // Add rules to root
        for (Map.Entry<Integer, Integer> entry: portRules.entrySet()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            root.add(node);
        }

        // Setup JTree model
        DefaultTreeModel model = new DefaultTreeModel(root);
        resourceTree.setModel(model);
    }

    @Override
    public void start() {
        mainFrame.setVisible(true);

        // start server
        new Thread((Runnable) server).start();

        // start gui updater
        this.run();
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void run() {
        // Setup timer and update rules statuses
        Timer timer = new Timer(1000, e -> {
            updateRulesStatus();
            SwingUtilities.updateComponentTreeUI(resourceTree);
        });
        timer.start();
    }

    //
    // Status renderer
    //

    private class MyRenderer extends DefaultTreeCellRenderer {
        private final Icon icon_up;
        private final Icon icon_down;
        private final Icon icon_no;
        private final Icon icon_ok;
        private final Icon icon_info;

        private ImageIcon loadIcon(String path) {
            URL iconURL = Gui.class.getResource(path);
            return new ImageIcon(iconURL);
        }

        MyRenderer() {
            icon_ok = loadIcon("/icons/icon_OK.png");
            icon_no = loadIcon("/icons/live_status_offline_icon.png");
            icon_info = loadIcon("/icons/icon_info.png");
            icon_up = loadIcon("/icons/arrow_up.png");
            icon_down = loadIcon("/icons/arrow_down.png");
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
                    hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getDepth() == 1) {
                // Get status object
                DefaultMutableTreeNode connected = (DefaultMutableTreeNode) node.getChildAt(0);

                // Dirty hack not to store status object inside JTree
                String connectionStatus = connected.toString();
                // Awful code next (checks last char of status string)
                // note sign o in cyrillic
                if (connectionStatus.charAt(connectionStatus.length() - 1) == 'о') {
                    setIcon(icon_ok);
                } else {
                    setIcon(icon_no);
                }
            }

            if (node.getDepth() == 0) {
                switch (node.getParent().getIndex(node)) {
                    case 0:
                        setIcon(icon_info);
                        break;
                    case 1:
                        setIcon(icon_down);
                        break;
                    case 2:
                        setIcon(icon_up);
                        break;
                    case 3:
                        // TBD
                        break;
                }

            }

            return this;
        }
    }

    //
    // Logger handler for transferring data to JTextArea
    //

    private final class TextComponentHandler extends Handler {
        private final JTextArea text;
        TextComponentHandler(JTextArea text) {
            this.text = text;
        }
        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record))
                synchronized(text) {
                    int port = (int)record.getParameters()[1];
                    // log if general info
                    if (port == -1 || record.getParameters().length == 1) {
                        text.append(record.getSourceClassName() + " " + record.getSourceMethodName() + "\n");
                        text.append(record.getParameters()[0] + "\n");
                        text.setCaretPosition(text.getDocument().getLength());
                    }
                    // log if rule selected

                    if (selectedPair.containsKey(port) || selectedPair.containsValue(port)) {
                        text.append(record.getSourceClassName() + " " + record.getSourceMethodName() + "\n");
                        text.append(record.getParameters()[0] + "\n");
                        text.setCaretPosition(text.getDocument().getLength());
                    }

                    // Tell logger scroll to scroll bottom
                    // TODO: user cannot scroll properly -> fix!
//                    logScroll.getVerticalScrollBar().addAdjustmentListener(e ->
//                            e.getAdjustable().setValue(e.getAdjustable().getMaximum()));

                }
        }
        @Override
        public void flush() {/**/}
        @Override
        public void close() throws SecurityException {/**/}
    }

    private final class TextPaneComponentHandler extends Handler {
        private final JTextPane text;
        SimpleAttributeSet keyWord = new SimpleAttributeSet();

        TextPaneComponentHandler(JTextPane text) {
            this.text = text;

            StyleConstants.setForeground(keyWord, Color.RED);
            StyleConstants.setBackground(keyWord, Color.YELLOW);
            StyleConstants.setBold(keyWord, true);
        }
        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record))
                synchronized(text) {
                    int port = (int)record.getParameters()[1];
                    // log if general info
                    if (port == -1 || record.getParameters().length == 1) {
                        HTMLDocument doc = (HTMLDocument) text.getStyledDocument();


                        String data = "<i>" + record.getSourceClassName() + "</i> " + record.getSourceMethodName() + "<br />" +
                                record.getParameters()[0] + "<br /><br />";

                        try {
                            doc.insertAfterEnd(doc.getCharacterElement(doc.getLength()), data);
                        } catch (BadLocationException | IOException e) {
                            e.printStackTrace();
                        }
                        // Tell logger scroll to scroll bottom
                        text.setCaretPosition(text.getDocument().getLength());
                    }
                    // log if rule selected

                    if (selectedPair.containsKey(port) || selectedPair.containsValue(port)) {
                        HTMLDocument doc = (HTMLDocument) text.getStyledDocument();

                        String data = "<br /><i>" + record.getSourceClassName() + "</i> " + record.getSourceMethodName() + "<br />" +
                                record.getParameters()[0] + "<br />";
                        try {
                            doc.insertAfterEnd(doc.getCharacterElement(doc.getLength()), data);
                        } catch (BadLocationException | IOException e) {
                            e.printStackTrace();
                        }
                        // Tell logger scroll to scroll bottom
                        text.setCaretPosition(text.getDocument().getLength());
                    }



                }
        }

        @Override
        public void flush() { }

        @Override
        public void close() throws SecurityException { }
    }
}
