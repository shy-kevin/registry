package com.shy;

import java.io.IOException;

/**
 * 注册表使用示例
 */
public class RegistryDemo {
    public static void main(String[] args) {
        // 获取单例注册表实例
        Registry registry = Registry.getInstance();
        
        try {
            // 创建一个注册表项
            String testPath = "HKEY_LOCAL_MACHINE\\SOFTWARE\\MyApplication_test";
            boolean created = registry.createKey(testPath);
            System.out.println("创建注册表项 " + testPath + "：" + (created ? "成功" : "失败"));
            
            // 设置值
            boolean valueSet = registry.setValue(testPath, "Version_test", "String", "2.0.0");
            System.out.println("设置Version值：" + (valueSet ? "成功" : "失败"));
            
            valueSet = registry.setValue(testPath, "InstallPath", "String", "C:\\Program Files\\MyApplication");
            System.out.println("设置InstallPath值：" + (valueSet ? "成功" : "失败"));
            
            valueSet = registry.setValue(testPath, "IsEnabled", "DWord", "1");
            System.out.println("设置IsEnabled值：" + (valueSet ? "成功" : "失败"));
            
//            // 读取值
//            RegistryValue versionValue = registry.getValue(testPath, "Version");
//            if (versionValue != null) {
//                System.out.println("读取Version值：" + versionValue.getValue());
//            }
            
            // 删除一个值
            boolean valueDeleted = registry.deleteValue(testPath, "IsEnabled");
            System.out.println("删除IsEnabled值：" + (valueDeleted ? "成功" : "失败"));
            
            // 保存注册表
            registry.saveToFile();
            System.out.println("注册表已保存到二进制文件");
            
            // 验证单例模式
            Registry anotherInstance = Registry.getInstance();
            System.out.println("是否为同一个实例：" + (registry == anotherInstance));

            // 遍历所有键值对
            System.out.println("遍历注册表中的所有键值对:");
            registry.traverseAllValues();
            
        } catch (IOException e) {
            System.out.println("操作注册表时出错：" + e.getMessage());
        }
    }
}
