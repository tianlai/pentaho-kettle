 /**********************************************************************
 **                                                                   **
 **               This code belongs to the KETTLE project.            **
 **                                                                   **
 ** Kettle, from version 2.2 on, is released into the public domain   **
 ** under the Lesser GNU Public License (LGPL).                       **
 **                                                                   **
 ** For more details, please read the document LICENSE.txt, included  **
 ** in this project                                                   **
 **                                                                   **
 ** http://www.kettle.be                                              **
 ** info@kettle.be                                                    **
 **                                                                   **
 **********************************************************************/
 
package org.pentaho.di.trans.steps.calculator;

import java.util.ArrayList;
import java.util.List;

import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueDataUtil;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

import be.ibridge.kettle.core.Const;
import be.ibridge.kettle.core.exception.KettleException;
import be.ibridge.kettle.core.exception.KettleStepException;
import be.ibridge.kettle.core.exception.KettleValueException;


/**
 * Calculate new field values using pre-defined functions. 
 * 
 * @author Matt
 * @since 8-sep-2005
 */
public class Calculator extends BaseStep implements StepInterface
{
    public class FieldIndexes
    {
        public int indexName;
        public int indexA;
        public int indexB;
        public int indexC;
    };    

	private CalculatorMeta meta;
	private CalculatorData data;

	public Calculator(StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta, Trans trans)
	{
		super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
	}

	public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException
	{
		meta=(CalculatorMeta)smi;
		data=(CalculatorData)sdi;

		Object[] r=getRow();    // get row, set busy!
		if (r==null)  // no more input to be expected...
		{
			setOutputDone();
			return false;
		}
        
        if (first)
        {
            first=false;
            data.outputRowMeta = (RowMetaInterface) getInputRowMeta().clone(); 
            meta.getFields(data.outputRowMeta, getStepname(), null);
            
            // get all metadata, including source rows and temporary fields.
            data.calcRowMeta = meta.getAllFields(getInputRowMeta()); 
            
            data.fieldIndexes = new FieldIndexes[meta.getCalculation().length];
            List tempIndexes = new ArrayList();

            // Calculate the indexes of the values and arguments in the target data or temporary data
            // We do this in advance to save time later on.
            //
            for (int i=0;i<meta.getCalculation().length;i++)
            {
                CalculatorMetaFunction function = meta.getCalculation()[i];
                data.fieldIndexes[i] = new FieldIndexes();
                
                if (!Const.isEmpty(function.getFieldName())) 
                {
                    data.fieldIndexes[i].indexName = data.outputRowMeta.indexOfValue(function.getFieldName());
                    if (data.fieldIndexes[i].indexName<0)
                    {
                        // Maybe it's a temporary field
                        data.fieldIndexes[i].indexName = data.tempRowMeta.indexOfValue(function.getFieldName());
                        if (data.fieldIndexes[i].indexName<0)
                        {
                            // Nope: throw an exception
                            throw new KettleStepException("Unable to find the specified fieldname '"+function.getFieldName()+" for calculation #"+(i+1));
                        }
                    }
                }
                else
                {
                    throw new KettleStepException("There is no name specified for calculated field #"+(i+1));
                }

                if (!Const.isEmpty(function.getFieldA())) 
                {
                    data.fieldIndexes[i].indexA = data.outputRowMeta.indexOfValue(function.getFieldA());
                    if (data.fieldIndexes[i].indexA<0)
                    {
                        // Maybe it's a temporary field
                        data.fieldIndexes[i].indexA = data.tempRowMeta.indexOfValue(function.getFieldA());
                        if (data.fieldIndexes[i].indexA<0)
                        {
                            // Nope: throw an exception
                            throw new KettleStepException("Unable to find the first argument field '"+function.getFieldName()+" for calculation #"+(i+1));
                        }
                    }
                }
                else
                {
                    throw new KettleStepException("There is no first argument specified for calculated field #"+(i+1));
                }

                if (!Const.isEmpty(function.getFieldB())) 
                {
                    data.fieldIndexes[i].indexB = data.outputRowMeta.indexOfValue(function.getFieldB());
                    if (data.fieldIndexes[i].indexB<0)
                    {
                        // Maybe it's a temporary field
                        data.fieldIndexes[i].indexB = data.tempRowMeta.indexOfValue(function.getFieldB());
                        if (data.fieldIndexes[i].indexB<0)
                        {
                            // Nope: throw an exception
                            throw new KettleStepException("Unable to find the second argument field '"+function.getFieldName()+" for calculation #"+(i+1));
                        }
                    }
                }
                
                if (!Const.isEmpty(function.getFieldC())) 
                {
                    data.fieldIndexes[i].indexC = data.outputRowMeta.indexOfValue(function.getFieldC());
                    if (data.fieldIndexes[i].indexC<0)
                    {
                        // Maybe it's a temporary field
                        data.fieldIndexes[i].indexC = data.tempRowMeta.indexOfValue(function.getFieldC());
                        if (data.fieldIndexes[i].indexC<0)
                        {
                            // Nope: throw an exception
                            throw new KettleStepException("Unable to find the third argument field '"+function.getFieldName()+" for calculation #"+(i+1));
                        }
                    }
                }
                                
                if (function.isRemovedFromResult())
                {
                    tempIndexes.add(new Integer(getInputRowMeta().size()+i));
                }
            }
            
            // Convert temp indexes to int[]
            data.tempIndexes = new int[tempIndexes.size()];
            for (int i=0;i<data.tempIndexes.length;i++)
            {
                data.tempIndexes[i] = ((Integer)tempIndexes.get(i)).intValue();
            }
        }

        if (log.isRowLevel()) log.logRowlevel(toString(), "Read row #"+linesRead+" : "+r);

        Object[] row = calcFields(getInputRowMeta(), data.outputRowMeta, r);		
		putRow(data.outputRowMeta, row);     // copy row to possible alternate rowset(s).

        if (log.isRowLevel()) log.logRowlevel(toString(), "Wrote row #"+linesWritten+" : "+r);        
        if (checkFeedback(linesRead)) logBasic("Linenr "+linesRead);

		return true;
	}

    /**
     * TODO: Implement it, make it backward compatible.
     * 
     * @param inputRowMeta
     * @param outputRowMeta
     * @param r
     * @return
     * @throws KettleValueException
     */
    private Object[] calcFields(RowMetaInterface inputRowMeta, RowMetaInterface outputRowMeta, Object[] r) throws KettleValueException
    {
        // First copy the input data to the new result...
        Object[] calcData = new Object[data.calcRowMeta.size()];
        for (int i=0;i<inputRowMeta.size();i++)
        {
            calcData[i] = r[i];
        }

        for (int i=0;i<meta.getCalculation().length;i++)
        {
            CalculatorMetaFunction fn = meta.getCalculation()[i];
            if (!Const.isEmpty(fn.getFieldName()))
            {
                // Get the metadata & the data...
                // ValueMetaInterface metaTarget = data.calcRowMeta.getValueMeta(i);
                
                ValueMetaInterface metaA=null;
                Object dataA=null;
                
                if (data.fieldIndexes[i].indexA>=0) 
                {
                    metaA = data.calcRowMeta.getValueMeta( data.fieldIndexes[i].indexA );
                    dataA = calcData[ data.fieldIndexes[i].indexA ];
                }

                ValueMetaInterface metaB=null;
                Object dataB=null;

                if (data.fieldIndexes[i].indexB>=0) 
                {
                    metaB = data.calcRowMeta.getValueMeta( data.fieldIndexes[i].indexB );
                    dataB = calcData[ data.fieldIndexes[i].indexB ];
                }

                ValueMetaInterface metaC=null;
                Object dataC=null;

                if (data.fieldIndexes[i].indexC>=0) 
                {
                    metaC = data.calcRowMeta.getValueMeta( data.fieldIndexes[i].indexC );
                    dataC = calcData[ data.fieldIndexes[i].indexC ];
                }
                
                // TODO: the data types are those of the first argument field, convert to the target field.
                // Exceptions: 
                //  - multiply can be string
                //  - constant is string
                //  - all date functions except add days/months
                //  - hex encode / decodes
                
                switch(fn.getCalcType())
                {
                case CalculatorMetaFunction.CALC_NONE: 
                    break;
                case CalculatorMetaFunction.CALC_ADD                :  // A + B
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.plus(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_SUBTRACT           :   // A - B
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.minus(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_MULTIPLY           :   // A * B
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.multiply(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_DIVIDE             :   // A / B
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.divide(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_SQUARE             :   // A * A
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.multiply(metaA, dataA, metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_SQUARE_ROOT        :   // SQRT( A )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.sqrt(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_PERCENT_1          :   // 100 * A / B 
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.percent1(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_PERCENT_2          :  // A - ( A * B / 100 )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.percent2(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_PERCENT_3          :  // A + ( A * B / 100 )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.percent2(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_COMBINATION_1      :  // A + B * C
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.combination1(metaA, dataA, metaB, dataB, metaC, dataC);
                    }
                    break;
                case CalculatorMetaFunction.CALC_COMBINATION_2      :  // SQRT( A*A + B*B )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.combination2(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_ROUND_1            :  // ROUND( A )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.round(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_ROUND_2            :  //  ROUND( A , B )
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.round(metaA, dataA, metaB, dataB);
                    }
                    break;
                case CalculatorMetaFunction.CALC_CONSTANT           : // Set field to constant value...
                    {
                        calcData[inputRowMeta.size()+i] = dataA; // A string
                    }
                    break;
                case CalculatorMetaFunction.CALC_NVL                : // Replace null values with another value
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.nvl(metaA, dataA, metaB, dataB);
                    }
                    break;                    
                case CalculatorMetaFunction.CALC_ADD_DAYS           : // Add B days to date field A
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.addDays(metaA, dataA, metaB, dataB);
                    }
                    break;
               case CalculatorMetaFunction.CALC_YEAR_OF_DATE           : // What is the year (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.yearOfDate(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_MONTH_OF_DATE           : // What is the month (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.monthOfDate(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_DAY_OF_YEAR           : // What is the day of year (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.dayOfYear(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_DAY_OF_MONTH           : // What is the day of month (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.dayOfMonth(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_DAY_OF_WEEK           : // What is the day of week (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.dayOfWeek(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_WEEK_OF_YEAR    : // What is the week of year (Integer) of a date?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.weekOfYear(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_WEEK_OF_YEAR_ISO8601   : // What is the week of year (Integer) of a date ISO8601 style?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.weekOfYearISO8601(metaA, dataA);
                    }
                    break;                    
                case CalculatorMetaFunction.CALC_YEAR_OF_DATE_ISO8601     : // What is the year (Integer) of a date ISO8601 style?
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.yearOfDateISO8601(metaA, dataA);
                    }
                    break;
                case CalculatorMetaFunction.CALC_BYTE_TO_HEX_ENCODE   : // Byte to Hex encode string field A
                    {
                        calcData[inputRowMeta.size()+i] = ValueDataUtil.byteToHexEncode(metaA, dataA);
                    }
                    break;
                }
                
                /*
                case CalculatorMetaFunction.CALC_HEX_TO_BYTE_DECODE   : // Hex to Byte decode string field A
                    {
                        value = new Value(fn.getFieldName(), fieldA);
                        value.hexToByteDecode();
                    }
                    break;                    
                case CalculatorMetaFunction.CALC_CHAR_TO_HEX_ENCODE   : // Char to Hex encode string field A
                    {
                        value = new Value(fn.getFieldName(), fieldA);
                        value.charToHexEncode();
                    }
                    break;
                case CalculatorMetaFunction.CALC_HEX_TO_CHAR_DECODE   : // Hex to Char decode string field A
                    {
                        value = new Value(fn.getFieldName(), fieldA);
                        value.hexToCharDecode();
                    }
                    break;                    
                default:
                    throw new KettleValueException("Unknown calculation type #"+fn.getCalcType());
                }
                
                if (value!=null)
                {
                    if (fn.getValueType()!=Value.VALUE_TYPE_NONE) 
                    {
                        value.setType(fn.getValueType());
                        value.setLength(fn.getValueLength(), fn.getValuePrecision());
                    }
                    r.addValue(value); // add to the row!
                }
                */
                
                // TODO: convert the data to the correct target data type.
                // 
            }
        }
        
        // OK, now we should refrain from adding the temporary fields to the result.
        // So we remove them.
        // 
        return RowDataUtil.removeItems(calcData, data.tempIndexes);
    }

	public boolean init(StepMetaInterface smi, StepDataInterface sdi)
	{
		meta=(CalculatorMeta)smi;
		data=(CalculatorData)sdi;
		
		if (super.init(smi, sdi))
		{
		    return true;
		}
		return false;
	}
	
	//
	// Run is were the action happens!
	public void run()
	{
		try
		{
			logBasic("Starting to run...");
			while (processRow(meta, data) && !isStopped());
		}
		catch(Exception e)
		{
			logError("Unexpected error in "+" : "+e.toString());
            logError(Const.getStackTracker(e));
            setErrors(1);
			stopAll();
		}
		finally
		{
			dispose(meta, data);
			logSummary();
			markStop();
		}
	}
}
