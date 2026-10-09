package com.ruoyi.generator.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.velocity.VelocityContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.GenConstants;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.generator.domain.GenTable;
import com.ruoyi.generator.domain.GenTableColumn;

/**
 * 模板处理工具类
 * 
 * @author ruoyi
 */
public class VelocityUtils
{
    /** 项目空间路径 */
    private static final String PROJECT_PATH = "main/java";

    /** mybatis空间路径 */
    private static final String MYBATIS_PATH = "main/resources/mapper";

    /** 默认上级菜单，系统工具 */
    private static final String DEFAULT_PARENT_MENU_ID = "3";

    /** Vue3 Element Plus 模版 */
    private static final String ELEMENT_PLUS = "element-plus";

    /** Vue3 Element Plus TypeScript 模版 */
    private static final String ELEMENT_PLUS_TYPESSRIPT = "element-plus-typescript";

    /** 逻辑删除字段名 */
    private static final String LOGIC_DELETE_COLUMN_NAME = "del_flag";

    /** 乐观锁版本字段名 */
    private static final String VERSION_COLUMN_NAME = "version";

    /**
     * 设置模板变量信息
     *
     * @return 模板列表
     */
    public static VelocityContext prepareContext(GenTable genTable)
    {
        String moduleName = genTable.getModuleName();
        String businessName = genTable.getBusinessName();
        String packageName = genTable.getPackageName();
        String tplCategory = genTable.getTplCategory();
        String functionName = genTable.getFunctionName();

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("tplCategory", genTable.getTplCategory());
        velocityContext.put("tableName", genTable.getTableName());
        velocityContext.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        velocityContext.put("ClassName", genTable.getClassName());
        velocityContext.put("className", StringUtils.uncapitalize(genTable.getClassName()));
        velocityContext.put("moduleName", genTable.getModuleName());
        velocityContext.put("BusinessName", StringUtils.capitalize(genTable.getBusinessName()));
        velocityContext.put("businessName", genTable.getBusinessName());
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", genTable.getFunctionAuthor());
        velocityContext.put("colSpan", getColSpan(genTable.getFormColNum()));
        velocityContext.put("datetime", DateUtils.getDate());
        velocityContext.put("pkColumn", genTable.getPkColumn());
        velocityContext.put("importList", getImportList(genTable));
        velocityContext.put("dtoImportList", getDtoVoImportList(genTable));
        velocityContext.put("voImportList", getDtoVoImportList(genTable));
        velocityContext.put("storageFileColumns", getStorageFileColumns(genTable));
        velocityContext.put("voStorageFileColumns", getVoStorageFileColumns(genTable));
        velocityContext.put("hasStorageFile", !getStorageFileColumns(genTable).isEmpty());
        velocityContext.put("hasVoStorageFile", !getVoStorageFileColumns(genTable).isEmpty());
        velocityContext.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        velocityContext.put("columns", genTable.getColumns());
        velocityContext.put("table", genTable);
        setMybatisPlusVelocityContext(velocityContext, genTable, "");
        velocityContext.put("dicts", getDicts(genTable));
        setExtensionsContext(velocityContext, genTable.getOptions());
        setMenuVelocityContext(velocityContext, genTable);
        if (GenConstants.TPL_TREE.equals(tplCategory))
        {
            setTreeVelocityContext(velocityContext, genTable);
        }
        if (GenConstants.TPL_SUB.equals(tplCategory))
        {
            setSubVelocityContext(velocityContext, genTable);
        }
        return velocityContext;
    }

    public static void setExtensionsContext(VelocityContext context, String options)
    {
        JSONObject paramsObj = JSONObject.parseObject(options);
        boolean genView = genView(paramsObj);
        context.put("genView", genView);
    }

    public static void setMenuVelocityContext(VelocityContext context, GenTable genTable)
    {
        String options = genTable.getOptions();
        JSONObject paramsObj = JSON.parseObject(options);
        String parentMenuId = getParentMenuId(paramsObj);
        context.put("parentMenuId", parentMenuId);
    }

    public static void setTreeVelocityContext(VelocityContext context, GenTable genTable)
    {
        String options = genTable.getOptions();
        JSONObject paramsObj = JSON.parseObject(options);
        String treeCode = getTreecode(paramsObj);
        String treeParentCode = getTreeParentCode(paramsObj);
        String treeName = getTreeName(paramsObj);

        context.put("treeCode", treeCode);
        context.put("treeParentCode", treeParentCode);
        context.put("treeName", treeName);
        context.put("expandColumn", getExpandColumn(genTable));
        if (paramsObj.containsKey(GenConstants.TREE_PARENT_CODE))
        {
            context.put("tree_parent_code", paramsObj.getString(GenConstants.TREE_PARENT_CODE));
        }
        if (paramsObj.containsKey(GenConstants.TREE_NAME))
        {
            context.put("tree_name", paramsObj.getString(GenConstants.TREE_NAME));
        }
    }

    public static void setSubVelocityContext(VelocityContext context, GenTable genTable)
    {
        GenTable subTable = genTable.getSubTable();
        String subTableName = genTable.getSubTableName();
        String subTableFkName = genTable.getSubTableFkName();
        String subClassName = genTable.getSubTable().getClassName();
        String subTableFkClassName = StringUtils.convertToCamelCase(subTableFkName);

        context.put("subTable", subTable);
        context.put("subTableName", subTableName);
        context.put("subTableFkName", subTableFkName);
        context.put("subTableFkClassName", subTableFkClassName);
        context.put("subTableFkclassName", StringUtils.uncapitalize(subTableFkClassName));
        context.put("subClassName", subClassName);
        context.put("subclassName", StringUtils.uncapitalize(subClassName));
        context.put("subImportList", getImportList(genTable.getSubTable()));
        setMybatisPlusVelocityContext(context, genTable.getSubTable(), "sub");
    }

    /**
     * 设置MyBatis-Plus逻辑删除和乐观锁模板变量，按表字段存在性条件生成。
     */
    public static void setMybatisPlusVelocityContext(VelocityContext context, GenTable genTable, String prefix)
    {
        GenTableColumn logicDeleteColumn = getColumnByColumnName(genTable, LOGIC_DELETE_COLUMN_NAME);
        GenTableColumn versionColumn = getColumnByColumnName(genTable, VERSION_COLUMN_NAME);
        // 只有数值型version字段才启用XML里的version + 1乐观锁逻辑，避免timestamp version被映射为Integer。
        boolean hasVersion = StringUtils.isNotNull(versionColumn) && isNumberVersionColumn(versionColumn);
        String keyPrefix = StringUtils.isEmpty(prefix) ? "" : prefix;
        String logicDeleteColumnKey = StringUtils.isEmpty(keyPrefix) ? "logicDeleteColumn" : keyPrefix + "LogicDeleteColumn";
        String versionColumnKey = StringUtils.isEmpty(keyPrefix) ? "versionColumn" : keyPrefix + "VersionColumn";
        String hasLogicDeleteKey = StringUtils.isEmpty(keyPrefix) ? "hasLogicDelete" : keyPrefix + "hasLogicDelete";
        String hasVersionKey = StringUtils.isEmpty(keyPrefix) ? "hasVersion" : keyPrefix + "hasVersion";
        // logicDeleteColumn保存del_flag字段元数据，hasLogicDelete表示当前表是否具备逻辑删除能力。
        context.put(logicDeleteColumnKey, logicDeleteColumn);
        // versionColumn保存version字段元数据，hasVersion表示当前表是否具备乐观锁能力。
        context.put(versionColumnKey, versionColumn);
        context.put(hasLogicDeleteKey, StringUtils.isNotNull(logicDeleteColumn));
        context.put(hasVersionKey, hasVersion);
    }

    /**
     * 判断version字段是否适合按数值型乐观锁生成。
     */
    private static boolean isNumberVersionColumn(GenTableColumn column)
    {
        return StringUtils.equalsAny(column.getJavaType(), GenConstants.TYPE_INTEGER, GenConstants.TYPE_LONG);
    }

    /**
     * 获取模板信息
     * @param tplCategory 生成的模板
     * @param tplWebType 前端类型
     * @return 模板列表
     */
    public static List<String> getTemplateList(GenTable table)
    {
        String tplWebType = table.getTplWebType();
        String tplCategory = table.getTplCategory();
        JSONObject paramsObj = JSONObject.parseObject(table.getOptions());
        boolean isView = genView(paramsObj);
        String useWebType = "vm/vue";
        String apiTemplate = "vm/js/api.js.vm";
        if (StringUtils.equals(ELEMENT_PLUS, tplWebType))
        {
            useWebType = "vm/vue/v3";
        }
        else if (StringUtils.equals(ELEMENT_PLUS_TYPESSRIPT, tplWebType))
        {
            useWebType = "vm/vue/v3ts";
            apiTemplate = "vm/ts/api.ts.vm";
        }
        List<String> templates = new ArrayList<String>();
        templates.add("vm/java/domain.java.vm");
        templates.add("vm/java/saveDto.java.vm");
        templates.add("vm/java/editDto.java.vm");
        templates.add("vm/java/queryDto.java.vm");
        templates.add("vm/java/vo.java.vm");
        templates.add("vm/java/convert.java.vm");
        templates.add("vm/java/mapper.java.vm");
        templates.add("vm/java/service.java.vm");
        templates.add("vm/java/serviceImpl.java.vm");
        templates.add("vm/java/controller.java.vm");
        templates.add("vm/xml/mapper.xml.vm");
        templates.add("vm/sql/sql.vm");
        templates.add(apiTemplate);
        if (StringUtils.equals(ELEMENT_PLUS_TYPESSRIPT, tplWebType))
        {
            templates.add("vm/ts/common.ts.vm");
            templates.add("vm/ts/type.ts.vm");
            templates.add("vm/ts/index.ts.vm");
        }
        if (GenConstants.TPL_CRUD.equals(tplCategory))
        {
            templates.add(useWebType + "/index.vue.vm");
        }
        else if (GenConstants.TPL_TREE.equals(tplCategory))
        {
            templates.add(useWebType + "/index-tree.vue.vm");
        }
        else if (GenConstants.TPL_SUB.equals(tplCategory))
        {
            templates.add(useWebType + "/index.vue.vm");
            templates.add("vm/java/sub-domain.java.vm");
        }
        if (isView)
        {
            templates.add(useWebType + "/view.vue.vm");
        }
        return templates;
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, GenTable genTable)
    {
        // 文件名称
        String fileName = "";
        // 包路径
        String packageName = genTable.getPackageName();
        // 模块名
        String moduleName = genTable.getModuleName();
        // 大写类名
        String className = genTable.getClassName();
        // 业务名称
        String businessName = genTable.getBusinessName();

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        String vuePath = "vue";

        if (template.endsWith("vm/java/domain.java.vm"))
        {
            fileName = StringUtils.format("{}/domain/{}.java", javaPath, className);
        }
        else if (template.endsWith("vm/java/sub-domain.java.vm") && StringUtils.equals(GenConstants.TPL_SUB, genTable.getTplCategory()))
        {
            fileName = StringUtils.format("{}/domain/{}.java", javaPath, genTable.getSubTable().getClassName());
        }
        else if (template.endsWith("vm/java/saveDto.java.vm"))
        {
            fileName = StringUtils.format("{}/dto/{}SaveDto.java", javaPath, className);
        }
        else if (template.endsWith("vm/java/editDto.java.vm"))
        {
            fileName = StringUtils.format("{}/dto/{}EditDto.java", javaPath, className);
        }
        else if (template.endsWith("vm/java/queryDto.java.vm"))
        {
            fileName = StringUtils.format("{}/dto/{}QueryDto.java", javaPath, className);
        }
        else if (template.endsWith("vm/java/vo.java.vm"))
        {
            fileName = StringUtils.format("{}/vo/{}Vo.java", javaPath, className);
        }
        else if (template.endsWith("vm/java/convert.java.vm"))
        {
            fileName = StringUtils.format("{}/convert/{}Convert.java", javaPath, className);
        }
        else if (template.contains("mapper.java.vm"))
        {
            fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
        }
        else if (template.contains("service.java.vm"))
        {
            fileName = StringUtils.format("{}/service/I{}Service.java", javaPath, className);
        }
        else if (template.contains("serviceImpl.java.vm"))
        {
            fileName = StringUtils.format("{}/service/impl/{}ServiceImpl.java", javaPath, className);
        }
        else if (template.contains("controller.java.vm"))
        {
            fileName = StringUtils.format("{}/controller/{}Controller.java", javaPath, className);
        }
        else if (template.contains("mapper.xml.vm"))
        {
            fileName = StringUtils.format("{}/{}Mapper.xml", mybatisPath, className);
        }
        else if (template.contains("sql.vm"))
        {
            fileName = businessName + "Menu.sql";
        }
        else if (template.contains("api.js.vm"))
        {
            fileName = StringUtils.format("{}/api/{}/{}.js", vuePath, moduleName, businessName);
        }
        else if (template.contains("api.ts.vm"))
        {
            fileName = StringUtils.format("{}/api/{}/{}.ts", vuePath, moduleName, businessName);
        }
        else if (template.contains("common.ts.vm"))
        {
            fileName = StringUtils.format("{}/types/api/common.ts", vuePath);
        }
        else if (template.contains("type.ts.vm"))
        {
            fileName = StringUtils.format("{}/types/api/{}/{}.ts", vuePath, moduleName, businessName);
        }
        else if (template.contains("index.ts.vm"))
        {
            fileName = StringUtils.format("{}/types/api/index-bak.ts", vuePath);
        }
        else if (template.contains("index.vue.vm"))
        {
            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
        }
        else if (template.contains("index-tree.vue.vm"))
        {
            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
        }
        else if (template.contains("view.vue.vm"))
        {
            fileName = StringUtils.format("{}/views/{}/{}/view.vue", vuePath, moduleName, businessName);
        }
        return fileName;
    }

    /**
     * 获取包前缀
     *
     * @param packageName 包名称
     * @return 包前缀名称
     */
    public static String getPackagePrefix(String packageName)
    {
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtils.substring(packageName, 0, lastIndex);
    }

    /**
     * 根据列类型获取导入包
     * 
     * @param genTable 业务表对象
     * @return 返回需要导入的包列表
     */
    public static HashSet<String> getImportList(GenTable genTable)
    {
        List<GenTableColumn> columns = genTable.getColumns();
        GenTable subGenTable = genTable.getSubTable();
        HashSet<String> importList = new HashSet<String>();
        // 生成Mapper继承BaseMapper后，实体统一生成@TableName并直接使用数据库原始表名。
        importList.add("com.baomidou.mybatisplus.annotation.TableName");
        if (StringUtils.isNotNull(subGenTable))
        {
            importList.add("java.util.List");
        }
        for (GenTableColumn column : columns)
        {
            if (!column.isSuperColumn() && GenConstants.TYPE_DATE.equals(column.getJavaType()))
            {
                importList.add("java.util.Date");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_LOCAL_DATE.equals(column.getJavaType()))
            {
                // LocalDate字段需要DateTimeFormat处理GET查询参数，JsonFormat处理JSON响应。
                importList.add("java.time.LocalDate");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
                importList.add("org.springframework.format.annotation.DateTimeFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_LOCAL_DATE_TIME.equals(column.getJavaType()))
            {
                // LocalDateTime字段需要DateTimeFormat处理GET查询参数，JsonFormat处理JSON响应。
                importList.add("java.time.LocalDateTime");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
                importList.add("org.springframework.format.annotation.DateTimeFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_BIGDECIMAL.equals(column.getJavaType()))
            {
                importList.add("java.math.BigDecimal");
            }
        }
        GenTableColumn versionColumn = getColumnByColumnName(genTable, VERSION_COLUMN_NAME);
        if (StringUtils.isNotNull(versionColumn) && isNumberVersionColumn(versionColumn))
        {
            // 只有数值型version才生成MP乐观锁注解，避免timestamp等同名字段被当作版本号。
            importList.add("com.baomidou.mybatisplus.annotation.Version");
        }
        return importList;
    }

    /**
     * 根据数据库字段名获取列信息。
     */
    /**
     * 获取DTO和VO需要导入的包列表，避免把实体专用的@TableName和@Version带入接口对象。
     *
     * @param genTable 业务表对象
     * @return 返回DTO和VO需要导入的包列表
     */
    public static HashSet<String> getDtoVoImportList(GenTable genTable)
    {
        List<GenTableColumn> columns = genTable.getColumns();
        HashSet<String> importList = new HashSet<String>();
        for (GenTableColumn column : columns)
        {
            if (!column.isSuperColumn() && GenConstants.TYPE_DATE.equals(column.getJavaType()))
            {
                importList.add("java.util.Date");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_LOCAL_DATE.equals(column.getJavaType()))
            {
                // DTO/VO同样用于GET查询和JSON返回，LocalDate需要同时声明两类格式化注解。
                importList.add("java.time.LocalDate");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
                importList.add("org.springframework.format.annotation.DateTimeFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_LOCAL_DATE_TIME.equals(column.getJavaType()))
            {
                // DTO/VO同样用于GET查询和JSON返回，LocalDateTime需要同时声明两类格式化注解。
                importList.add("java.time.LocalDateTime");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
                importList.add("org.springframework.format.annotation.DateTimeFormat");
            }
            else if (!column.isSuperColumn() && GenConstants.TYPE_BIGDECIMAL.equals(column.getJavaType()))
            {
                importList.add("java.math.BigDecimal");
            }
        }
        return importList;
    }

    /**
     * 获取需要纳入文件生命周期管理的字段。
     * <p>
     * 代码生成页面中选择图片上传或文件上传的字段，默认认为业务表保存 fileId/fileIds。
     */
    public static List<GenTableColumn> getStorageFileColumns(GenTable genTable)
    {
        List<GenTableColumn> result = new ArrayList<GenTableColumn>();
        if (StringUtils.isNull(genTable) || StringUtils.isEmpty(genTable.getColumns()))
        {
            return result;
        }
        for (GenTableColumn column : genTable.getColumns())
        {
            if (isStorageFileColumn(column))
            {
                result.add(column);
            }
        }
        return result;
    }

    /**
     * 获取 VO 中需要额外生成 xxxList 回显字段的文件字段。
     * <p>
     * 只有列表显示=true 且选择上传组件的字段才生成 xxxList，避免接口返回无用文件授权信息。
     */
    public static List<GenTableColumn> getVoStorageFileColumns(GenTable genTable)
    {
        List<GenTableColumn> result = new ArrayList<GenTableColumn>();
        if (StringUtils.isNull(genTable) || StringUtils.isEmpty(genTable.getColumns()))
        {
            return result;
        }
        for (GenTableColumn column : genTable.getColumns())
        {
            if (column.isList() && isStorageFileColumn(column))
            {
                result.add(column);
            }
        }
        return result;
    }

    /**
     * 判断字段是否使用文件资产上传组件。
     */
    private static boolean isStorageFileColumn(GenTableColumn column)
    {
        return StringUtils.isNotNull(column) && StringUtils.equalsAny(column.getHtmlType(),
                GenConstants.HTML_IMAGE_UPLOAD, GenConstants.HTML_FILE_UPLOAD);
    }

    public static GenTableColumn getColumnByColumnName(GenTable genTable, String columnName)
    {
        if (StringUtils.isNull(genTable) || StringUtils.isEmpty(columnName))
        {
            return null;
        }
        List<GenTableColumn> columns = genTable.getColumns();
        if (StringUtils.isEmpty(columns))
        {
            return null;
        }
        for (GenTableColumn column : columns)
        {
            if (StringUtils.equalsIgnoreCase(column.getColumnName(), columnName))
            {
                return column;
            }
        }
        return null;
    }

    /**
     * 根据列类型获取字典组
     * 
     * @param genTable 业务表对象
     * @return 返回字典组
     */
    public static String getDicts(GenTable genTable)
    {
        List<GenTableColumn> columns = genTable.getColumns();
        Set<String> dicts = new HashSet<String>();
        addDicts(dicts, columns);
        if (StringUtils.isNotNull(genTable.getSubTable()))
        {
            List<GenTableColumn> subColumns = genTable.getSubTable().getColumns();
            addDicts(dicts, subColumns);
        }
        return StringUtils.join(dicts, ", ");
    }

    /**
     * 添加字典列表
     * 
     * @param dicts 字典列表
     * @param columns 列集合
     */
    public static void addDicts(Set<String> dicts, List<GenTableColumn> columns)
    {
        for (GenTableColumn column : columns)
        {
            if (!column.isSuperColumn() && StringUtils.isNotEmpty(column.getDictType()) && StringUtils.equalsAny(
                    column.getHtmlType(),
                    new String[] { GenConstants.HTML_SELECT, GenConstants.HTML_RADIO, GenConstants.HTML_CHECKBOX }))
            {
                dicts.add("'" + column.getDictType() + "'");
            }
        }
    }

    /**
     * 获取权限前缀
     *
     * @param moduleName 模块名称
     * @param businessName 业务名称
     * @return 返回权限前缀
     */
    public static String getPermissionPrefix(String moduleName, String businessName)
    {
        return StringUtils.format("{}:{}", moduleName, businessName);
    }

    /**
     * 获取上级菜单ID字段
     *
     * @param paramsObj 生成其他选项
     * @return 上级菜单ID字段
     */
    public static String getParentMenuId(JSONObject paramsObj)
    {
        if (StringUtils.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.PARENT_MENU_ID)
                && StringUtils.isNotEmpty(paramsObj.getString(GenConstants.PARENT_MENU_ID)))
        {
            return paramsObj.getString(GenConstants.PARENT_MENU_ID);
        }
        return DEFAULT_PARENT_MENU_ID;
    }

    /**
     * 获取树编码
     *
     * @param paramsObj 生成其他选项
     * @return 树编码
     */
    public static String getTreecode(JSONObject paramsObj)
    {
        if (paramsObj.containsKey(GenConstants.TREE_CODE))
        {
            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_CODE));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取树父编码
     *
     * @param paramsObj 生成其他选项
     * @return 树父编码
     */
    public static String getTreeParentCode(JSONObject paramsObj)
    {
        if (paramsObj.containsKey(GenConstants.TREE_PARENT_CODE))
        {
            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_PARENT_CODE));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 扩展功能/生成详情页
     * 
     * @param paramsObj 生成其他选项
     * @return 是否生成详细页
     */
    public static boolean genView(JSONObject paramsObj)
    {
        if (StringUtils.isNotNull(paramsObj) && paramsObj.containsKey(GenConstants.GEN_VIEW))
        {
            return paramsObj.getBoolean(GenConstants.GEN_VIEW);
        }
        return false;
    }

    /**
     * 获取树名称
     *
     * @param paramsObj 生成其他选项
     * @return 树名称
     */
    public static String getTreeName(JSONObject paramsObj)
    {
        if (paramsObj.containsKey(GenConstants.TREE_NAME))
        {
            return StringUtils.toCamelCase(paramsObj.getString(GenConstants.TREE_NAME));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取需要在哪一列上面显示展开按钮
     *
     * @param genTable 业务表对象
     * @return 展开按钮列序号
     */
    public static int getExpandColumn(GenTable genTable)
    {
        String options = genTable.getOptions();
        JSONObject paramsObj = JSON.parseObject(options);
        String treeName = paramsObj.getString(GenConstants.TREE_NAME);
        int num = 0;
        for (GenTableColumn column : genTable.getColumns())
        {
            if (column.isList())
            {
                num++;
                String columnName = column.getColumnName();
                if (columnName.equals(treeName))
                {
                    break;
                }
            }
        }
        return num;
    }

    /**
     * 获取表单 el-col span
     * 
     * @param formColNum 表单布局方式（1单列 2双列 3三列）
     * @return span 数值字符串
     */
    public static String getColSpan(int formColNum)
    {
        if (formColNum == 2)
        {
            return "12";
        }
        else if (formColNum == 3)
        {
            return "8";
        }
        return "24";
    }
}
