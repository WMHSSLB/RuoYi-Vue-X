package com.ruoyi.system.domain.read;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

/**
 * 状态字符串处理。
 * <p>
 * 数据库中保存的是数字编码，导入导出时转换为用户可读的中文状态。
 *
 * @author ruoyi
 */
@SuppressWarnings("rawtypes")
public class StatusConverter implements Converter<Integer>
{
    /**
     * 声明Java字段类型，EasyExcel据此匹配Integer状态字段。
     */
    @Override
    public Class supportJavaTypeKey()
    {
        return Integer.class;
    }

    /**
     * 声明Excel单元格类型，状态在表格中以中文字符串展示。
     */
    @Override
    public CellDataTypeEnum supportExcelTypeKey()
    {
        return CellDataTypeEnum.STRING;
    }

    /**
     * 导出时将系统内部状态编码转换为Excel中文状态。
     */
    @Override
    public WriteCellData<String> convertToExcelData(Integer value, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration)
    {
        // EasyExcel 4.x将旧版CellData拆分为ReadCellData/WriteCellData。
        return new WriteCellData<>(value == null || 0 == value ? "正常" : "异常");
    }

    /**
     * 导入时将Excel中文状态还原为系统内部数字编码。
     */
    @Override
    public Integer convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration) throws Exception
    {
        return "正常".equals(cellData.getStringValue()) ? 0 : 1;
    }
}
