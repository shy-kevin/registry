package com.shy;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

/**
 * 注册表可视化编辑器
 * 类似Windows的regedit.exe，提供注册表的可视化浏览和编辑功能
 */
public class RegistryEditor extends JFrame {
    private Registry registry;
    private JTree registryTree;
    private DefaultTreeModel treeModel;
    private JTable valuesTable;
    private ValuesTableModel valuesTableModel;
    
    public RegistryEditor() {
        // 初始化注册表实例
        registry = Registry.getInstance();
        
        // 设置窗口基本属性
        setTitle("注册表编辑器");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示
        
        // 初始化UI组件
        initUI();
        
        // 加载注册表数据到树中
        loadRegistryData();
    }
    
    /**
     * 初始化用户界面组件
     */
    private void initUI() {
        // 创建菜单栏
        createMenuBar();
        
        // 创建主分割面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(300); // 设置初始分割位置
        
        // 左侧：注册表项树
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("注册表");
        treeModel = new DefaultTreeModel(rootNode);
        registryTree = new JTree(treeModel);
        registryTree.setCellRenderer(new RegistryTreeCellRenderer());
        registryTree.addTreeSelectionListener(e -> onTreeSelectionChanged());
        
        // 为树添加右键菜单
        registryTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = registryTree.getRowForLocation(e.getX(), e.getY());
                    registryTree.setSelectionRow(row);
                    showTreeContextMenu(e.getX(), e.getY());
                }
            }
        });
        
        JScrollPane treeScrollPane = new JScrollPane(registryTree);
        mainSplitPane.setLeftComponent(treeScrollPane);
        
        // 右侧：键值对表格
        valuesTableModel = new ValuesTableModel();
        valuesTable = new JTable(valuesTableModel);
        valuesTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        valuesTable.getTableHeader().setReorderingAllowed(false);
        
        // 为表格添加双击编辑功能
        valuesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = valuesTable.getSelectedRow();
                    if (row != -1) {
                        editSelectedValue(row);
                    }
                }
            }
        });
        
        JScrollPane tableScrollPane = new JScrollPane(valuesTable);
        mainSplitPane.setRightComponent(tableScrollPane);
        
        // 添加主面板到窗口
        getContentPane().add(mainSplitPane);
    }
    
    /**
     * 创建菜单栏
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        JMenuItem saveItem = new JMenuItem("保存");
        saveItem.addActionListener(e -> saveRegistry());
        
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // 编辑菜单
        JMenu editMenu = new JMenu("编辑");
        JMenuItem newKeyItem = new JMenuItem("新建项");
        newKeyItem.addActionListener(e -> createNewKey());
        
        JMenuItem newValueItem = new JMenuItem("新建值");
        newValueItem.addActionListener(e -> createNewValue());
        
        JMenuItem deleteItem = new JMenuItem("删除");
        deleteItem.addActionListener(e -> deleteSelectedItem());
        
        editMenu.add(newKeyItem);
        editMenu.add(newValueItem);
        editMenu.addSeparator();
        editMenu.add(deleteItem);
        
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        
        setJMenuBar(menuBar);
    }
    
    /**
     * 从注册表加载数据到树中
     */
    private void loadRegistryData() {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
        rootNode.removeAllChildren();
        
        // 添加顶级节点
        for (RegistryKey topKey : registry.getTopLevelKeys().values()) {
            DefaultMutableTreeNode topNode = new DefaultMutableTreeNode(topKey);
            rootNode.add(topNode);
            // 递归添加子节点
            loadSubKeys(topKey, topNode);
        }
        
        treeModel.reload();
        
        // 展开所有顶级节点
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            registryTree.expandRow(i + 1); // +1 因为根节点是隐藏的
        }
    }
    
    /**
     * 递归加载子项
     */
    private void loadSubKeys(RegistryKey parentKey, DefaultMutableTreeNode parentNode) {
        for (RegistryKey subKey : parentKey.getSubKeys().values()) {
            DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(subKey);
            parentNode.add(subNode);
            loadSubKeys(subKey, subNode); // 递归加载子项
        }
    }
    
    /**
     * 当树选择改变时更新右侧表格
     */
    private void onTreeSelectionChanged() {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            valuesTableModel.clear();
            return;
        }
        
        DefaultMutableTreeNode selectedNode = 
            (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        
        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof RegistryKey) {
            RegistryKey selectedKey = (RegistryKey) userObject;
            updateValuesTable(selectedKey);
        }
    }
    
    /**
     * 更新右侧表格显示选中项的键值对
     */
    private void updateValuesTable(RegistryKey key) {
        valuesTableModel.setValues(key.getValues());
    }
    
    /**
     * 显示树的右键菜单
     */
    private void showTreeContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem newKeyItem = new JMenuItem("新建项");
        newKeyItem.addActionListener(e -> createNewKey());
        
        JMenuItem newValueItem = new JMenuItem("新建值");
        newValueItem.addActionListener(e -> createNewValue());
        
        JMenuItem deleteItem = new JMenuItem("删除");
        deleteItem.addActionListener(e -> deleteSelectedItem());
        
        menu.add(newKeyItem);
        menu.add(newValueItem);
        menu.addSeparator();
        menu.add(deleteItem);
        
        menu.show(registryTree, x, y);
    }
    
    /**
     * 创建新的注册表项
     */
    private void createNewKey() {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个父项");
            return;
        }
        
        DefaultMutableTreeNode parentNode = 
            (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        
        if (!(parentNode.getUserObject() instanceof RegistryKey)) {
            JOptionPane.showMessageDialog(this, "请选择一个有效的父项");
            return;
        }
        
        String keyName = JOptionPane.showInputDialog(this, "请输入新项名称:");
        if (keyName == null || keyName.trim().isEmpty()) {
            return;
        }
        
        RegistryKey parentKey = (RegistryKey) parentNode.getUserObject();
        
        // 检查是否已存在同名项
        if (parentKey.getSubKey(keyName) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名项");
            return;
        }
        
        // 创建新项
        RegistryKey newKey = new RegistryKey(keyName);
        parentKey.addSubKey(newKey);
        
        // 更新树
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newKey);
        treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());
        
        // 展开父节点并选中新节点
        registryTree.expandPath(selectionPath);
        registryTree.setSelectionPath(new TreePath(newNode.getPath()));
        
        // 保存注册表
        saveRegistry();
    }
    
    /**
     * 创建新的键值对
     */
    private void createNewValue() {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个注册表项");
            return;
        }
        
        DefaultMutableTreeNode selectedNode = 
            (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        
        if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
            JOptionPane.showMessageDialog(this, "请选择一个有效的注册表项");
            return;
        }
        
        RegistryKey selectedKey = (RegistryKey) selectedNode.getUserObject();
        
        // 获取键名
        String valueName = JOptionPane.showInputDialog(this, "请输入键名:");
        if (valueName == null || valueName.trim().isEmpty()) {
            return;
        }
        
        // 检查是否已存在同名键
        if (selectedKey.getValue(valueName) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名键");
            return;
        }
        
        // 获取键值
        String valueData = JOptionPane.showInputDialog(this, "请输入键值:");
        if (valueData == null) {
            return;
        }
        
        // 获取值类型
        String[] valueTypes = {"String", "DWord", "QWord", "Binary", "Multi-String"};
        String valueType = (String) JOptionPane.showInputDialog(
            this, "选择值类型:", "值类型",
            JOptionPane.QUESTION_MESSAGE, null,
            valueTypes, valueTypes[0]);
        
        if (valueType == null) {
            return;
        }
        
        // 创建新键值对
        selectedKey.addValue(new RegistryValue(valueName, valueType, valueData));
        
        // 更新表格
        updateValuesTable(selectedKey);
        
        // 保存注册表
        saveRegistry();
    }
    
    /**
     * 编辑选中的键值对
     */
    private void editSelectedValue(int row) {
        String valueName = (String) valuesTableModel.getValueAt(row, 0);
        String currentType = (String) valuesTableModel.getValueAt(row, 1);
        String currentValue = (String) valuesTableModel.getValueAt(row, 2);
        
        // 获取选中的注册表项
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            return;
        }
        
        DefaultMutableTreeNode selectedNode = 
            (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        
        if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
            return;
        }
        
        RegistryKey selectedKey = (RegistryKey) selectedNode.getUserObject();
        RegistryValue value = selectedKey.getValue(valueName);
        
        if (value == null) {
            return;
        }
        
        // 显示编辑对话框
        String newValue = JOptionPane.showInputDialog(
            this, "编辑 " + valueName + " 的值:", currentValue);
        
        if (newValue != null) {
            value.setValue(newValue);
            updateValuesTable(selectedKey);
            saveRegistry();
        }
    }
    
    /**
     * 删除选中的项或值
     */
    private void deleteSelectedItem() {
        // 检查是否有选中的树节点
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的项");
            return;
        }
        
        DefaultMutableTreeNode selectedNode = 
            (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        
        if (selectedNode.getUserObject() instanceof RegistryKey) {
            // 删除注册表项
            int confirm = JOptionPane.showConfirmDialog(
                this, "确定要删除这项及其所有子项吗?", 
                "确认删除", JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                RegistryKey keyToDelete = (RegistryKey) selectedNode.getUserObject();
                String keyPath = getKeyPath(selectedNode);
                
                // 从注册表中删除
                registry.deleteKey(keyPath);
                
                // 从树中删除
                treeModel.removeNodeFromParent(selectedNode);
                
                // 清空表格
                valuesTableModel.clear();
                
                // 保存注册表
                saveRegistry();
            }
        }
    }
    
    /**
     * 获取注册表项的完整路径
     */
    private String getKeyPath(DefaultMutableTreeNode node) {
        StringBuilder path = new StringBuilder();
        TreePath treePath = new TreePath(node.getPath());
        Object[] pathComponents = treePath.getPath();
        
        // 跳过根节点("注册表")
        for (int i = 1; i < pathComponents.length; i++) {
            DefaultMutableTreeNode componentNode = (DefaultMutableTreeNode) pathComponents[i];
            RegistryKey key = (RegistryKey) componentNode.getUserObject();
            
            if (i > 1) {
                path.append("\\");
            }
            path.append(key.getName());
        }
        
        return path.toString();
    }
    
    /**
     * 保存注册表到文件
     */
    private void saveRegistry() {
        try {
            registry.saveToFile();
            System.out.println("注册表已保存");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this, "保存注册表失败: " + e.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /**
     * 注册表树的单元格渲染器
     */
    private class RegistryTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded,
                                              leaf, row, hasFocus);
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();
            
            // 设置节点显示文本为注册表项名称
            if (userObject instanceof RegistryKey) {
                setText(((RegistryKey) userObject).getName());
            }
            
            return this;
        }
    }
    
    /**
     * 键值对表格的数据模型
     */
    private class ValuesTableModel extends javax.swing.table.AbstractTableModel {
        private String[] columnNames = {"名称", "类型", "数据"};
        private Object[][] data;
        
        public ValuesTableModel() {
            data = new Object[0][3];
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public int getRowCount() {
            return data.length;
        }
        
        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            return data[row][col];
        }
        
        @Override
        public boolean isCellEditable(int row, int col) {
            return false; // 单元格不可直接编辑，通过双击触发编辑对话框
        }
        
        /**
         * 设置表格数据
         */
        public void setValues(Map<String, RegistryValue> values) {
            if (values == null || values.isEmpty()) {
                data = new Object[0][3];
                fireTableDataChanged();
                return;
            }
            
            data = new Object[values.size()][3];
            int row = 0;
            
            for (RegistryValue value : values.values()) {
                data[row][0] = value.getName();
                data[row][1] = value.getType();
                data[row][2] = value.getValue();
                row++;
            }
            
            fireTableDataChanged();
        }
        
        /**
         * 清空表格数据
         */
        public void clear() {
            data = new Object[0][3];
            fireTableDataChanged();
        }
    }
    
    /**
     * 主方法，启动注册表编辑器
     */
    public static void main(String[] args) {
        // 在事件调度线程中启动UI
        SwingUtilities.invokeLater(() -> {
            new RegistryEditor().setVisible(true);
        });
    }
}
