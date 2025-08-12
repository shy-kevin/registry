package com.shy;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

        // 为树添加右键菜单（新增重命名项）
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

        // 为表格添加右键菜单（新增重命名键值）
        valuesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = valuesTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        showTableContextMenu(e.getX(), e.getY(), row);
                    }
                }
            }
        });

        // 为表格添加双击编辑功能（原逻辑保留）
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

        // 导出子菜单（原逻辑保留）
        JMenu exportMenu = new JMenu("导出");
        JMenuItem exportAllItem = new JMenuItem("导出全部注册表");
        exportAllItem.addActionListener(e -> exportRegistry(true));
        JMenuItem exportSelectedItem = new JMenuItem("导出所选项");
        exportSelectedItem.addActionListener(e -> exportRegistry(false));
        exportMenu.add(exportAllItem);
        exportMenu.add(exportSelectedItem);

        // 新增“导入注册表”选项
        JMenuItem importItem = new JMenuItem("导入注册表");
        importItem.addActionListener(e -> importRegistry());

        // 保存退出
        JMenuItem saveItem = new JMenuItem("保存");
        saveItem.addActionListener(e -> saveRegistry());

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportMenu);
        fileMenu.add(importItem); // 插入导入选项
        fileMenu.addSeparator();
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

    //----------------------------------备份与导出部分的代码----------------------------------------------

    /**
     * 导出注册表（支持全部导出和选中项导出）
     * @param exportAll 是否导出全部注册表
     */
    private void exportRegistry(boolean exportAll) {
        // 确定要导出的注册表项
        RegistryKey exportKey = null;
        String defaultFileName = "registry_backup.reg";

        if (!exportAll) {
            TreePath selectionPath = registryTree.getSelectionPath();
            if (selectionPath == null) {
                JOptionPane.showMessageDialog(this, "请先选择要导出的注册表项");
                return;
            }

            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) selectionPath.getLastPathComponent();

            if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
                JOptionPane.showMessageDialog(this, "请选择有效的注册表项");
                return;
            }

            exportKey = (RegistryKey) selectedNode.getUserObject();
            defaultFileName = exportKey.getName() + "_backup.reg";
        }

        // 显示文件选择器
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(exportAll ? "导出全部注册表" : "导出所选项");
        fileChooser.setSelectedFile(new File(defaultFileName));

        // 添加.reg文件过滤器
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".reg");
            }

            @Override
            public String getDescription() {
                return "注册表文件 (*.reg)";
            }
        });

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            // 确保文件以.reg结尾
            if (!fileToSave.getName().toLowerCase().endsWith(".reg")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".reg");
            }

            // 检查文件是否已存在
            if (fileToSave.exists()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "文件已存在，是否覆盖？", "确认覆盖",
                        JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            try {
                // 导出注册表内容到文件
                exportRegistryToFile(fileToSave, exportAll, exportKey);

                JOptionPane.showMessageDialog(this,
                        "注册表已成功导出到:\n" + fileToSave.getAbsolutePath(),
                        "导出成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "导出注册表失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * 将注册表内容导出到文件（类似Windows的.reg格式）
     */
    private void exportRegistryToFile(File file, boolean exportAll, RegistryKey exportKey)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_16LE))) {

            // 写入REG文件头部
            writer.write("DataOS Registry Editor Version 1.00");
            writer.newLine();
            writer.newLine();

            if (exportAll) {
                // 导出全部注册表
                for (RegistryKey topKey : registry.getTopLevelKeys().values()) {
                    exportRegistryKey(writer, topKey, topKey.getName());
                }
            } else {
                // 导出选中的注册表项
                String keyPath = getKeyPath(exportKey);
                exportRegistryKey(writer, exportKey, keyPath);
            }
        }
    }

    /**
     * 递归导出注册表项及其子项
     */
    private void exportRegistryKey(BufferedWriter writer, RegistryKey key, String fullPath)
            throws IOException {
        // 写入项路径
        writer.write("[\"" + fullPath + "\"]");
        writer.newLine();

        // 写入键值对
        for (RegistryValue value : key.getValues().values()) {
            String valueStr;
            switch (value.getType()) {
                case "String":
                case "Multi-String":
                    valueStr = "\"" + escapeValue(value.getValue()) + "\"";
                    break;
                case "DWord":
                    valueStr = "dword:" + value.getValue().toLowerCase();
                    break;
                case "QWord":
                    valueStr = "hex(7):" + value.getValue().toLowerCase();
                    break;
                case "Binary":
                    valueStr = "hex:" + value.getValue();
                    break;
                default:
                    valueStr = "\"" + escapeValue(value.getValue()) + "\"";
            }

            // 处理默认值
            if (value.getName().isEmpty() || value.getName().equals("@")) {
                writer.write("@=" + valueStr);
            } else {
                writer.write("\"" + value.getName() + "\"=" + valueStr);
            }
            writer.newLine();
        }

        writer.newLine();

        // 递归导出子项
        String childPath;
        for (RegistryKey childKey : key.getSubKeys().values()) {
            childPath = fullPath + "\\" + childKey.getName();
            exportRegistryKey(writer, childKey, childPath);
        }
    }

    /**
     * 转义值中的特殊字符
     */
    private String escapeValue(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 获取注册表项的完整路径
     */
    private String getKeyPath(RegistryKey key) {
        // 查找顶级节点
        for (Map.Entry<String, RegistryKey> entry : registry.getTopLevelKeys().entrySet()) {
            String path = findKeyPath(entry.getValue(), key, entry.getKey());
            if (path != null) {
                return path;
            }
        }
        return key.getName();
    }

    /**
     * 递归查找注册表项的完整路径
     */
    private String findKeyPath(RegistryKey currentKey, RegistryKey targetKey, String currentPath) {
        if (currentKey == targetKey) {
            return currentPath;
        }

        for (RegistryKey childKey : currentKey.getSubKeys().values()) {
            String path = findKeyPath(childKey, targetKey, currentPath + "\\" + childKey.getName());
            if (path != null) {
                return path;
            }
        }

        return null;
    }
    //----------------------------------备份与导出部分的代码----------------------------------------------

    //----------------------------------导入注册表代码----------------------------------------------

    /**
     * 导入注册表（从 .reg 文件加载并合并到当前注册表）
     */
    private void importRegistry() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导入注册表文件");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".reg");
            }

            @Override
            public String getDescription() {
                return "注册表文件 (*.reg)";
            }
        });

        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File importFile = fileChooser.getSelectedFile();
            try {
                importRegistryFromFile(importFile);
                JOptionPane.showMessageDialog(this,
                        "注册表导入成功！\n文件：" + importFile.getAbsolutePath(),
                        "导入成功", JOptionPane.INFORMATION_MESSAGE);
                // 导入后刷新树结构
                loadRegistryData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "导入失败：" + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * 从 .reg 文件解析并导入注册表内容
     * @throws IOException  文件读取错误
     * @throws RegistryImportException  格式不兼容或解析错误
     */
    private void importRegistryFromFile(File file)
            throws IOException, RegistryImportException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_16LE);
        if (lines.isEmpty()) {
            throw new RegistryImportException("空的 .reg 文件");
        }

        // 检查文件头部（兼容格式）
        if (!lines.get(0).trim().equals("DataOS Registry Editor Version 1.00")) {
            throw new RegistryImportException("不支持的 .reg 文件格式");
        }

        RegistryKey currentKey = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            // 解析注册表项路径（如 ["HKEY_CURRENT_USER\\Software\\MyApp"]）
            if (line.startsWith("[") && line.endsWith("]")) {
                String path = line.substring(1, line.length() - 1)
                        .replace("\"", "") // 处理 Windows 导出的带引号路径
                        .replace("\\\\", "\\"); // 处理转义反斜杠
                currentKey = resolveRegistryKey(path);
                if (currentKey == null) {
                    throw new RegistryImportException("无效的注册表路径：" + path);
                }
            }
            // 解析键值对（如 "Name"="Value" 或 @="DefaultValue"）
            else if (currentKey != null) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    throw new RegistryImportException("无效的键值对格式：" + line);
                }
                String namePart = parts[0].trim().replace("\"", "");
                String valuePart = parts[1].trim();

                // 处理默认值（@ 符号）
                String valueName = namePart.equals("@") ? "" : namePart;
                // 解析值内容（自动识别类型）
                RegistryValue value = parseRegistryValue(valuePart);
                if (value != null) {
                    // 若存在同名键值，覆盖；否则新增
                    currentKey.getValues().put(valueName, value);
                }
            }
        }
        // 导入后保存当前注册表状态
        saveRegistry();
    }

    /**
     * 根据路径查找或创建注册表项（自动处理顶级节点匹配）
     * @return  找到或创建的 RegistryKey，若路径无效返回 null
     */
    private RegistryKey resolveRegistryKey(String fullPath) {
        // 分割路径（如 "HKEY_CURRENT_USER\\Software\\MyApp" -> 拆分多级）
        String[] pathParts = fullPath.split("\\\\");
        if (pathParts.length == 0) return null;

        // 处理顶级节点（如 "HKEY_CURRENT_USER" 匹配 registry.topLevelKeys）
        String topLevelName = pathParts[0];
        RegistryKey topKey = registry.getTopLevelKeys().get(topLevelName);
        if (topKey == null) {
            // 若顶级节点不存在，自动创建（或根据需求调整为抛异常）
            topKey = new RegistryKey(topLevelName);
            registry.getTopLevelKeys().put(topLevelName, topKey);
        }

        // 递归创建/查找子项
        RegistryKey current = topKey;
        for (int i = 1; i < pathParts.length; i++) {
            String part = pathParts[i];
            RegistryKey child = current.getSubKeys().get(part);
            if (child == null) {
                child = new RegistryKey(part);
                current.getSubKeys().put(part, child);
            }
            current = child;
        }
        return current;
    }

    /**
     * 解析 .reg 文件中的值内容，自动识别类型
     * @return  RegistryValue 或 null（无效格式）
     */
    private RegistryValue parseRegistryValue(String valueStr) {
        // 处理空值
        if (valueStr.equals("\"\"")) {
            return new RegistryValue("", "String", "");
        }
        // 处理带引号的字符串（如 "Hello\\World"）
        if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
            String value = valueStr.substring(1, valueStr.length() - 1)
                    .replace("\\\\", "\\")  // 恢复转义反斜杠
                    .replace("\\n", "\n")   // 恢复换行符
                    .replace("\\r", "\r");  // 恢复回车符
            return new RegistryValue("", "String", value);
        }
        // 处理 DWord（如 dword:00000001）
        if (valueStr.toLowerCase().startsWith("dword:")) {
            String hex = valueStr.substring(6);
            return new RegistryValue("", "DWord", hex);
        }
        // 处理 QWord（如 hex(7):00,00,00,00,00,00,00,01）
        if (valueStr.toLowerCase().startsWith("hex(7):")) {
            String hex = valueStr.substring(6).replace(",", "");
            return new RegistryValue("", "QWord", hex);
        }
        // 处理 Binary（如 hex:00,01,02）
        if (valueStr.toLowerCase().startsWith("hex:")) {
            String hex = valueStr.substring(4).replace(",", "");
            return new RegistryValue("", "Binary", hex);
        }
        // 处理 Multi-String（如 "Value1\0Value2\0" 或 hex(7):00,01...）
        // （简化处理：这里默认按 String 类型，实际可扩展自动识别）
        return new RegistryValue("", "String", valueStr.replace("\"", ""));
    }

    // 自定义异常：导入失败时抛出
    private static class RegistryImportException extends Exception {
        public RegistryImportException(String message) {
            super(message);
        }
    }

    //----------------------------------导入注册表代码----------------------------------------------

    //----------------------------------注册表展示部分的代码----------------------------------------------

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

        JMenuItem renameItem = new JMenuItem("重命名项");
        renameItem.addActionListener(e -> renameRegistryKey());

        JMenuItem exportItem = new JMenuItem("导出所选项");
        exportItem.addActionListener(e -> exportRegistry(false));

        JMenuItem deleteItem = new JMenuItem("删除");
        deleteItem.addActionListener(e -> deleteSelectedItem());

        menu.add(newKeyItem);
        menu.add(newValueItem);
        menu.add(renameItem);
        menu.add(exportItem);
        menu.addSeparator();
        menu.add(deleteItem);

        menu.show(registryTree, x, y);
    }

    /**
     * 显示表格的右键菜单（新增“重命名键值”）
     */
    private void showTableContextMenu(int x, int y, int row) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameValueItem = new JMenuItem("重命名键值");
        renameValueItem.addActionListener(e -> renameKeyValue(row));

        JMenuItem deleteValueItem = new JMenuItem("删除键值");
        deleteValueItem.addActionListener(e -> deleteSelectedValue(row));

        menu.add(renameValueItem);
        menu.add(deleteValueItem);

        menu.show(valuesTable, x, y);
    }

    //----------------------------------注册表展示部分的代码----------------------------------------------

    //----------------------------------重命名部分的代码----------------------------------------------

    /**
     * 重命名注册表项（核心逻辑）
     */
    private void renameRegistryKey() {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请选择要重命名的项");
            return;
        }

        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) selectionPath.getLastPathComponent();

        if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
            JOptionPane.showMessageDialog(this, "请选择有效的注册表项");
            return;
        }

        RegistryKey selectedKey = (RegistryKey) selectedNode.getUserObject();
        String oldName = selectedKey.getName();
        RegistryKey parentKey = getParentRegistryKey(selectedNode);

        // 弹出对话框输入新名称
        String newName = (String) JOptionPane.showInputDialog(this, "输入新名称:", "重命名项",
                JOptionPane.PLAIN_MESSAGE, null, null, oldName);

        if (newName == null || newName.trim().isEmpty() || newName.equals(oldName)) {
            return; // 取消或名称未变
        }

        // 检查父项中是否已存在同名子项（不区分大小写）
        if (parentKey != null && parentKey.getSubKey(newName) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名项");
            return;
        }

        // 1. 从父项的 subKeys 中移除旧名称的项
        if (parentKey != null) {
            parentKey.getSubKeys().remove(oldName); // 移除旧键
        }

        // 2. 修改当前 RegistryKey 的名称
        selectedKey.setName(newName);

        // 3. 以新名称为键，重新添加到父项的 subKeys
        if (parentKey != null) {
            parentKey.getSubKeys().put(newName, selectedKey); // 新增新键
        }

        // 4. 更新树节点显示（修改 UserObject 并触发刷新）
        selectedNode.setUserObject(selectedKey);
        treeModel.nodeChanged(selectedNode);

        // 5. 保存注册表
        saveRegistry();
    }

    /**
     * 获取选中节点的父 RegistryKey（辅助重命名逻辑）
     */
    private RegistryKey getParentRegistryKey(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
        if (parentNode == null) return null;

        Object parentObj = parentNode.getUserObject();
        return parentObj instanceof RegistryKey ? (RegistryKey) parentObj : null;
    }



    /**
     * 重命名键值名称（核心逻辑）
     */
    private void renameKeyValue(int row) {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请先选择注册表项");
            return;
        }

        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) selectionPath.getLastPathComponent();

        if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
            JOptionPane.showMessageDialog(this, "请选择有效的注册表项");
            return;
        }

        RegistryKey selectedKey = (RegistryKey) selectedNode.getUserObject();
        String oldValueName = (String) valuesTableModel.getValueAt(row, 0);
        RegistryValue oldValue = selectedKey.getValue(oldValueName);

        if (oldValue == null) {
            JOptionPane.showMessageDialog(this, "键值不存在");
            return;
        }

        // 弹出对话框输入新名称
        String newValueName = (String) JOptionPane.showInputDialog(this, "输入新键值名称:", "重命名键值",
                JOptionPane.PLAIN_MESSAGE, null, null, oldValueName);

        if (newValueName == null || newValueName.trim().isEmpty() || newValueName.equals(oldValueName)) {
            return; // 取消或名称未变
        }

        // 检查是否已存在同名键值
        if (selectedKey.getValue(newValueName) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名键值");
            return;
        }

        // 1. 删除旧键值
        selectedKey.removeValue(oldValueName);

        // 2. 创建新名称的键值（保留类型和值）
        RegistryValue newValue = new RegistryValue(
                newValueName, oldValue.getType(), oldValue.getValue()
        );
        selectedKey.addValue(newValue);

        // 3. 更新表格显示
        updateValuesTable(selectedKey);

        // 4. 保存注册表
        saveRegistry();
    }

    //----------------------------------重命名部分的代码----------------------------------------------

    //----------------------------------增添注册表项的代码----------------------------------------------

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
    //----------------------------------增添注册表项的代码----------------------------------------------

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

    //----------------------------------删除部分的代码----------------------------------------------
    /**
     * 删除选中的项
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
                String keyPath = getKeyPathFromNode(selectedNode);

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
     * 删除选中的键值（原逻辑优化，支持表格右键删除）
     */
    private void deleteSelectedValue(int row) {
        TreePath selectionPath = registryTree.getSelectionPath();
        if (selectionPath == null) {
            JOptionPane.showMessageDialog(this, "请先选择注册表项");
            return;
        }

        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) selectionPath.getLastPathComponent();

        if (!(selectedNode.getUserObject() instanceof RegistryKey)) {
            JOptionPane.showMessageDialog(this, "请选择有效的注册表项");
            return;
        }

        RegistryKey selectedKey = (RegistryKey) selectedNode.getUserObject();
        String valueName = (String) valuesTableModel.getValueAt(row, 0);

        // 删除键值
        selectedKey.removeValue(valueName);
        updateValuesTable(selectedKey);
        saveRegistry();
    }
    //----------------------------------删除部分的代码----------------------------------------------

    /**
     * 获取注册表项的完整路径
     */
    private String getKeyPathFromNode(DefaultMutableTreeNode node) {
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
