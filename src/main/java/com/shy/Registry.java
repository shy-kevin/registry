package com.shy;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 注册表核心类 - 单例模式实现
 * 负责管理注册表项和键值对，提供增删改查操作
 */
public class Registry implements Serializable {
    // 序列化版本号
    private static final long serialVersionUID = 1L;

    // 单例实例
    private static Registry instance;

    // 注册表文件路径
    private static final String REGISTRY_FILE = "registry.dat";

    // 顶级注册表项
    private Map<String, RegistryKey> topLevelKeys;

    // 私有构造方法，防止外部实例化
    private Registry() {
        topLevelKeys = new HashMap<>();
        initializeTopLevelKeys();
    }

    // 获取单例实例
    public static synchronized Registry getInstance() {
        if (instance == null) {
            // 先检查注册表文件是否存在
            File regFile = new File(REGISTRY_FILE);
            if (regFile.exists() && regFile.length() > 0) {
                // 文件存在，尝试加载
                try {
                    instance = loadFromFile();
                    System.out.println("成功从现有文件加载注册表");
                } catch (Exception e) {
                    System.err.println("加载注册表文件失败，将创建新注册表: " + e.getMessage());
                    instance = new Registry();
                }
            } else {
                // 文件不存在，创建新注册表
                System.out.println("未找到注册表文件，创建新注册表");
                instance = new Registry();
            }
        }
        return instance;
    }

    // 初始化顶级注册表项，类似Windows的HKEY_*
    private void initializeTopLevelKeys() {
        topLevelKeys.put("HKEY_MACHINE", new RegistryKey("HKEY_MACHINE"));
        topLevelKeys.put("HKEY_SOFTWARE", new RegistryKey("HKEY_SOFTWARE"));
        topLevelKeys.put("HKEY_USERS", new RegistryKey("HKEY_USERS"));
    }

    // 根据路径创建注册表项（可以创建多级子健）
    public boolean createKey(String path) {
        String[] parts = path.split("\\\\");
        if (parts.length < 1) {
            return false;
        }

        // 检查顶级节点是否存在
        RegistryKey currentKey = topLevelKeys.get(parts[0]);
        if (currentKey == null) {
            return false;
        }

        // 逐级创建子项
        for (int i = 1; i < parts.length; i++) {
            String keyName = parts[i];
            RegistryKey childKey = currentKey.getSubKey(keyName);  // 判断当前的键是否包含该子项
            if (childKey == null) {
                childKey = new RegistryKey(keyName);  // 没有就创建子项
                currentKey.addSubKey(childKey);        // 添加到父项的子项集合里
            }
            currentKey = childKey;    // 移动到子项
        }
        return true;
    }

    // 设置键值对
    public boolean setValue(String keyPath, String valueName, String type, String value) {
        RegistryKey key = getKeyByPath(keyPath);
        if (key == null) {
            return false;
        }

        key.addValue(new RegistryValue(valueName, type, value));
        return true;
    }

    // 根据路径获取注册表项
    public RegistryKey getKeyByPath(String path) {
        String[] parts = path.split("\\\\");
        if (parts.length < 1) {
            return null;
        }

        RegistryKey currentKey = topLevelKeys.get(parts[0]);
        if (currentKey == null) {
            return null;
        }

        for (int i = 1; i < parts.length; i++) {
            currentKey = currentKey.getSubKey(parts[i]);
            if (currentKey == null) {
                return null;
            }
        }
        return currentKey;
    }

    // 获取键值
    public RegistryValue getValue(String keyPath, String valueName) {
        RegistryKey key = getKeyByPath(keyPath);
        if (key == null) {
            return null;
        }
        return key.getValue(valueName);
    }

    // 删除键值
    public boolean deleteValue(String keyPath, String valueName) {
        RegistryKey key = getKeyByPath(keyPath);
        if (key == null) {
            return false;
        }
        return key.removeValue(valueName);
    }

    // 删除注册表项
    public boolean deleteKey(String keyPath) {
        String[] parts = keyPath.split("\\\\");
        if (parts.length <= 1) { // 顶级节点不能删
            return false;
        }

        // 如果是顶级节点，直接删除
//        if (parts.length == 1) {
//            return topLevelKeys.remove(parts[0]) != null;
//        }

        // 找到父节点
        String parentPath = "";
        for (int i = 0; i < parts.length - 1; i++) {
            parentPath += (i > 0 ? "\\" : "") + parts[i];
        }

        RegistryKey parentKey = getKeyByPath(parentPath);
        if (parentKey == null) {
            return false;
        }

        return parentKey.removeSubKey(parts[parts.length - 1]);
    }

    // 新增方法：遍历所有键值对
    public void traverseAllValues() {
        for (RegistryKey rootKey : topLevelKeys.values()) {
            traverseRegistryKey(rootKey, "");
        }
    }

    // 递归遍历注册表项及其子项
    private void traverseRegistryKey(RegistryKey key, String path) {
        String fullPath = path.isEmpty() ? key.getName() : path + "\\" + key.getName();
        for (RegistryValue value : key.getValues().values()) {
            System.out.println("路径: " + fullPath + ", 键: " + value.getName() + ", 值: " + value.getValue());
        }
        for (RegistryKey subKey : key.getSubKeys().values()) {
            traverseRegistryKey(subKey, fullPath);
        }
    }

    // 获取顶级注册表项
    public Map<String, RegistryKey> getTopLevelKeys() {
        return topLevelKeys;
    }

    // 从二进制文件加载注册表
    private static Registry loadFromFile() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(REGISTRY_FILE))) {
            return (Registry) ois.readObject();
        }
    }

    // 保存注册表到二进制文件
    public void saveToFile() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(REGISTRY_FILE))) {
            oos.writeObject(this);
        }
    }
}
