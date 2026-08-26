package com.ruoyi.system.domain.read;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

/**
 * 操作类别字符串处理。
 * <p>
 * 数据库中保存的是数字编码，导入导出时转换为用户可读的中文操作类别。
 *
 * @author ruoyi
 */
@SuppressWarnings("rawtypes")
public class OperTypeConverter implements Converter<Integer>
{
    /**
     * 声明Java字段类型，EasyExcel据此匹配Integer操作类别字段。
     */
    @Override
    public Class supportJavaTypeKey()
    {
        return Integer.class;
    }

    /**
     * 声明Excel单元格类型，操作类别在表格中以中文字符串展示。
     */
    @Override
    public CellDataTypeEnum supportExcelTypeKey()
    {
        return CellDataTypeEnum.STRING;
    }

    /**
     * 导入时将Excel中文操作类别还原为系统内部数字编码。
     */
    @Override
    public Integer convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration)
    {
        Integer value = 0;
        String str = cellData.getStringValue();
        if ("后台用户".equals(str))
        {
            value = 1;
        }
        else if ("手机端用户".equals(str))
        {
            value = 2;
        }
        return value;
    }

    /**
     * 导出时将系统内部数字编码转换为Excel中文操作类别。
     */
    @Override
    public WriteCellData<String> convertToExcelData(Integer value, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration)
    {
        String str = "其他";
        if (value == null || 0 == value)
        {
            // EasyExcel 4.x将旧版CellData拆分为ReadCellData/WriteCellData。
            return new WriteCellData<>(str);
        }
        if (1 == value)
        {
            str = "后台用户";
        }
        else if (2 == value)
        {
            str = "手机端用户";
        }
        return new WriteCellData<>(str);
    }
}
