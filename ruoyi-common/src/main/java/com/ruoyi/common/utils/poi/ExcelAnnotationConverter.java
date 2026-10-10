package com.ruoyi.common.utils.poi;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.common.utils.StringUtils;

/**
 * EasyExcel通用字段转换器。
 * <p>
 * 代码生成模板会同时保留若依原有@Excel注解和新增@ExcelProperty注解，本转换器负责读取@Excel中的
 * readConverterExp、dictType、dateFormat等配置，让EasyExcel导入导出尽量复用原POI导出规则。
 *
 * @author 955Kane
 */
public class ExcelAnnotationConverter implements Converter<Object>
{
    @Override
    public Class<?> supportJavaTypeKey()
    {
        // 注解指定converter时EasyExcel会优先使用该转换器，这里声明Object以覆盖常见字段类型。
        return Object.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey()
    {
        // 经过@Excel规则翻译后的值统一按字符串写入Excel。
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Object convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration)
    {
        Field field = contentProperty.getField();
        Excel excel = getExcel(field);
        String cellValue = cellData.getStringValue();
        if (excel != null)
        {
            if (StringUtils.isNotEmpty(excel.readConverterExp()))
            {
                cellValue = ExcelUtil.reverseByExp(cellValue, excel.readConverterExp(), excel.separator());
            }
            else if (StringUtils.isNotEmpty(excel.dictType()))
            {
                cellValue = DictUtils.getDictValue(excel.dictType(), cellValue, excel.separator());
            }
        }
        return convertToFieldType(cellValue, field);
    }

    @Override
    public WriteCellData<String> convertToExcelData(Object value, ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration)
    {
        Field field = contentProperty.getField();
        Excel excel = getExcel(field);
        String cellValue = Convert.toStr(value);
        if (excel != null && StringUtils.isNotNull(value))
        {
            if (StringUtils.isNotEmpty(excel.dateFormat()))
            {
                cellValue = parseDateToStr(excel.dateFormat(), value);
            }
            else if (StringUtils.isNotEmpty(excel.readConverterExp()))
            {
                cellValue = ExcelUtil.convertByExp(Convert.toStr(value), excel.readConverterExp(), excel.separator());
            }
            else if (StringUtils.isNotEmpty(excel.dictType()))
            {
                cellValue = DictUtils.getDictLabel(excel.dictType(), Convert.toStr(value), excel.separator());
            }
            else if (value instanceof BigDecimal && -1 != excel.scale())
            {
                cellValue = Convert.toStr(((BigDecimal) value).setScale(excel.scale(), excel.roundingMode()));
            }
        }
        return new WriteCellData<>(cellValue);
    }

    /**
     * 获取字段上的若依@Excel注解，避免模板重复描述EasyExcel转换规则。
     */
    private Excel getExcel(Field field)
    {
        return field == null ? null : field.getAnnotation(Excel.class);
    }

    /**
     * 将Excel字符串值转换为实体字段类型，保证EasyExcel导入时能写回数字、日期等常见字段。
     */
    private Object convertToFieldType(String value, Field field)
    {
        if (field == null || value == null)
        {
            return value;
        }
        Class<?> fieldType = field.getType();
        if (String.class == fieldType)
        {
            return value;
        }
        if (Integer.TYPE == fieldType || Integer.class == fieldType)
        {
            return Convert.toInt(value);
        }
        if (Long.TYPE == fieldType || Long.class == fieldType)
        {
            return Convert.toLong(value);
        }
        if (Double.TYPE == fieldType || Double.class == fieldType)
        {
            return Convert.toDouble(value);
        }
        if (Float.TYPE == fieldType || Float.class == fieldType)
        {
            return Convert.toFloat(value);
        }
        if (BigDecimal.class == fieldType)
        {
            return Convert.toBigDecimal(value);
        }
        if (Date.class == fieldType)
        {
            return DateUtils.parseDate(value);
        }
        if (Boolean.TYPE == fieldType || Boolean.class == fieldType)
        {
            return Convert.toBool(value);
        }
        if (LocalDateTime.class == fieldType)
        {
            return LocalDateTime.parse(value.replace(" ", "T"));
        }
        if (LocalDate.class == fieldType)
        {
            return LocalDate.parse(value);
        }
        return value;
    }

    /**
     * 按@Excel(dateFormat)格式化不同日期类型，兼容Date、LocalDate和LocalDateTime。
     */
    private String parseDateToStr(String dateFormat, Object value)
    {
        if (value instanceof Date)
        {
            return DateUtils.parseDateToStr(dateFormat, (Date) value);
        }
        if (value instanceof LocalDateTime)
        {
            return DateUtils.parseDateToStr(dateFormat, DateUtils.toDate((LocalDateTime) value));
        }
        if (value instanceof LocalDate)
        {
            return DateUtils.parseDateToStr(dateFormat, DateUtils.toDate((LocalDate) value));
        }
        return Convert.toStr(value);
    }
}
