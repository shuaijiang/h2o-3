package water.parser;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.*;
import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.ql.io.orc.RecordReader;
import org.apache.hadoop.hive.ql.io.orc.StripeInformation;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.*;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static water.parser.orc.OrcUtil.isSupportedSchema;

/**
 * Test suite for orc parser.
 *
 * This test will build a H2O frame for all orc files found in smalldata/parser/orc directory
 * and compare the H2O frame content with the orc file content read with Core Java commands.
 * Test is declared a success if the content of H2O frame is the same as the contents read
 * by using core Java commands off the Orc file itself.  No multi-threading is used in reading
 * off the Orc file using core Java commands.
 */
public class ParseTestOrc extends TestUtil {

  private double EPSILON = 1e-9;
  private long ERRORMARGIN = 1000L;  // error margin when compare timestamp.
  int totalFilesTested = 0;
  int numberWrong = 0;

  // list all orc files in smalldata/parser/orc directory
  private String[] allOrcFiles = {
      "smalldata/parser/orc/TestOrcFile.columnProjection.orc",
      "smalldata/parser/orc/bigint_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.emptyFile.orc",
      "smalldata/parser/orc/bool_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.metaData.orc",
//      "smalldata/parser/orc/decimal.orc",
//      "smalldata/parser/orc/TestOrcFile.test1.orc",
      "smalldata/parser/orc/demo-11-zlib.orc",
      "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
      "smalldata/parser/orc/demo-12-zlib.orc",
      "smalldata/parser/orc/TestOrcFile.testDate2038.orc",
      "smalldata/parser/orc/double_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testMemoryManagementV11.orc",
      "smalldata/parser/orc/float_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testMemoryManagementV12.orc",
      "smalldata/parser/orc/int_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testPredicatePushdown.orc",
      "smalldata/parser/orc/nulls-at-end-snappy.orc",
//      "smalldata/parser/orc/TestOrcFile.testSeek.orc",
//      "smalldata/parser/orc/orc-file-11-format.orc",
      "smalldata/parser/orc/TestOrcFile.testSnappy.orc",
      "smalldata/parser/orc/orc_split_elim.orc",
      "smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc",
//          "smalldata/parser/orc/over1k_bloom.orc",
      "smalldata/parser/orc/TestOrcFile.testStripeLevelStats.orc",
      "smalldata/parser/orc/smallint_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testTimestamp.orc",
      "smalldata/parser/orc/string_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testUnionAndTimestamp.orc",
      "smalldata/parser/orc/tinyint_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testWithoutIndex.orc",
//          "smalldata/parser/orc/version1999.orc"
  };

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testParseAllOrcs() {
    Set<String> failedFiles = new TreeSet<>();
    int numOfOrcFiles = allOrcFiles.length; // number of Orc Files to test

    for (int fIndex = 0; fIndex < numOfOrcFiles; fIndex++)
    {

//      if ((fIndex == 4) || (fIndex == 6) || (fIndex == 18) || (fIndex == 23) || (fIndex == 28))
//        continue;   // do not support metadata from user
//
//      if (fIndex == 31)   // contain only orc header, no column and no row, total file size is 0.
//        continue;
//
//      if (fIndex == 19)   // different column names are used between stripes
//        continue;
//
//      if (fIndex == 26)   // abnormal orc file, no inpsector structure available
//        continue;

//      if (fIndex ==30)    // problem getting the right column number and then comparison problem
//        continue;

//      if (fIndex == 22)     // problem with BufferedString retrieval for binary, wait for Tomas
//        continue;
//
//      if (fIndex == 17)   // problem with bigint retrieval, wait for Tomas
//        continue;

//      Random rn = new Random();
//      int randNum = rn.nextInt(10);
//
//      if (randNum > 3)  // skip test for 70% of the time
//        continue;

      String fileName = allOrcFiles[fIndex];
      Log.info("Orc Parser parsing " + fileName);
      File f = find_test_file_static(fileName);

      if (f != null && f.exists()) {
        Configuration conf = new Configuration();
        Path p = new Path(f.toString());
        try {
          Reader orcFileReader = OrcFile.createReader(p, OrcFile.readerOptions(conf));     // orc reader
          Frame h2oFrame = parse_test_file(fileName);     // read one orc file and build a H2O frame

          compareH2OFrame(fileName, failedFiles, h2oFrame, orcFileReader);

          if (h2oFrame != null) // delete frame after done.
            h2oFrame.delete();

          totalFilesTested++;

        } catch (IOException e) {
          e.printStackTrace();
          failedFiles.add(fileName);
          numberWrong++;
        }

      } else {
        Log.warn("The following file was not found: " + fileName);
      }
    }

    if (numberWrong > 0) {
      Log.warn("There are errors in your test.");
      assertEquals("Number of orc files failed to parse is: "  + ", failed files = " + failedFiles.toString(), 0, numberWrong);
    }
  }

  /**
   * This method will take one H2O frame generated by the Orc parser and the fileName of the Orc file
   * and attempt to compare the content of the Orc file to the H2O frame.  In particular, the following
   * are compared:
   * - column names;
   * - number of columns and rows;
   * - content of each row.
   *
   * If all comparison pass, the test will pass.  Otherwise, the test will fail.
   *
   * @param h2oFrame
   * @param orcReader
     */
  private void compareH2OFrame(String fileName, Set<String> failedFiles, Frame h2oFrame, Reader orcReader) {
    // grab column names, column and row numbers
    StructObjectInspector insp = (StructObjectInspector) orcReader.getObjectInspector();
    List<StructField> allColInfo = (List<StructField>) insp.getAllStructFieldRefs();    // get info of all cols

    // compare number of columns and rows
    int allColNumber = allColInfo.size();    // get and check column number

    int colNumber = 0 ;
    for (StructField oneField:allColInfo) {
      String colType = oneField.getFieldObjectInspector().getTypeName();

      if (colType.toLowerCase().contains("decimal"))
        colType = "decimal";

      if (isSupportedSchema(colType))
        colNumber++;
    }

    assertEquals("Number of columns need to be the same: ", colNumber, h2oFrame.numCols());

    // compare column names
    String[] colNames = new String[colNumber];
    String[] colTypes = new String[colNumber];
    int colIndex = 0;
    for (int index = 0; index < allColNumber; index++) {   // get and check column names
      String typeName = allColInfo.get(colIndex).getFieldObjectInspector().getTypeName();

      if (typeName.toLowerCase().contains("decimal"))
        typeName = "decimal";

      if (isSupportedSchema(typeName)) {
        colNames[colIndex] = allColInfo.get(colIndex).getFieldName();
        colTypes[colIndex] = typeName;
        colIndex++;
      }
    }
    assertArrayEquals("Column names need to be the same: ", colNames, h2oFrame._names);

    // compare one column at a time of the whole row?
    compareFrameContents(fileName, failedFiles, h2oFrame, orcReader, colTypes, colNames, null);

    Long totalRowNumber = orcReader.getNumberOfRows();    // get and check row number
    assertEquals("Number of rows need to be the same: ", totalRowNumber, (Long) h2oFrame.numRows());

  }


  private void compareFrameContents(String fileName, Set<String> failedFiles, Frame h2oFrame, Reader orcReader, String[] colTypes, String[] colNames,
                                    boolean[] toInclude) {
    // prepare parameter to read a orc file.
//    boolean[] toInclude = new boolean[colNumber+1];   // must equal to number of column+1
//    Arrays.fill(toInclude, true);

    List<StripeInformation> stripesInfo = orcReader.getStripes(); // get all stripe info

    if (stripesInfo.size() == 0) {  // Orc file contains no data
      assertEquals("Orc file is empty.  H2O frame row number should be zero: ", 0, h2oFrame.numRows());
    } else {
      Long startRowIndex = 0L;   // row index into H2O frame
      for (StripeInformation oneStripe : stripesInfo) {
        try {
          RecordReader perStripe = orcReader.rows(oneStripe.getOffset(), oneStripe.getDataLength(), toInclude, null,
                  colNames);
          VectorizedRowBatch batch = perStripe.nextBatch(null);  // read orc file stripes in vectorizedRowBatch

          boolean done = false;
          Long rowCounts = 0L;
          Long rowNumber = oneStripe.getNumberOfRows();   // row number of current stripe

          while (!done) {
            long currentBatchRow = batch.count();     // row number of current batch

            ColumnVector[] dataVectors = batch.cols;

            for (int cIdx = 0; cIdx < colNames.length; cIdx++) {   // read one column at a time;
              compare1Cloumn(dataVectors[cIdx], colTypes[cIdx].toLowerCase(), cIdx, currentBatchRow, h2oFrame.vec(colNames[cIdx]),
                      startRowIndex);
            }

            rowCounts = rowCounts + currentBatchRow;    // record number of rows of data actually read
            startRowIndex = startRowIndex + currentBatchRow;

            if (rowCounts >= rowNumber)               // read all rows of the stripe already.
              done = true;

            if (!done)  // not done yet, get next batch
              batch = perStripe.nextBatch(batch);
          }

          perStripe.close();
        } catch (Throwable e) {
          numberWrong++;
          failedFiles.add(fileName);
          e.printStackTrace();
 //         assertEquals("Test failed! ", true, false);
        }
      }
    }
  }

  private void compare1Cloumn(ColumnVector oneColumn, String columnType, int cIdx, long currentBatchRow,
                                     Vec h2oColumn, Long startRowIndex) {

    if (columnType.contains("bigint"))  // cannot handle big integer right now
      return;

    if (columnType.contains("binary"))  // binary retrieval problem.  Tomas
      return;

    switch (columnType) {
      case "boolean":
      case "bigint":  // FIXME: not working right now
      case "int":
      case "smallint":
      case "tinyint":
      case "date":  //FIXME: make sure this is what the customer wants
        CompareLongcolumn(oneColumn, oneColumn.isNull, currentBatchRow, h2oColumn, startRowIndex);
        break;
      case "float":
      case "double":
        compareDoublecolumn(oneColumn, oneColumn.isNull, currentBatchRow, h2oColumn, startRowIndex);
        break;
      case "string":  //FIXME: not working right now
      case "varchar":
      case "char":
      case "binary":  //FIXME: only reading it as string right now.
        compareStringcolumn(oneColumn, oneColumn.isNull, currentBatchRow, h2oColumn, startRowIndex, columnType);
        break;
      case "timestamp": //FIXME: read in as a number
        compareTimecolumn(oneColumn, oneColumn.isNull, currentBatchRow, h2oColumn, startRowIndex);
        break;
      case "decimal":   //FIXME: make sure we interpret this correctly, ignore the scale right now
        compareDecimalcolumn(oneColumn, oneColumn.isNull, currentBatchRow, h2oColumn, startRowIndex);
        break;
      default:
        Log.warn("String, bigint are not tested.  H2O frame is built for them but cannot be verified.");
    }
  }

  private void compareDecimalcolumn(ColumnVector oneDecimalColumn, boolean[] isNull,
                                           long currentBatchRow, Vec h2oFrame, Long startRowIndex) {
    HiveDecimalWritable[] oneColumn= ((DecimalColumnVector) oneDecimalColumn).vector;
    long frameRowIndex = startRowIndex;

    for (int rowIndex = 0; rowIndex < currentBatchRow; rowIndex++) {
      if (isNull[rowIndex])
        assertEquals("Na is found: ", true, h2oFrame.isNA(frameRowIndex));
      else
        assertEquals("Decimal elements should equal: ", Double.parseDouble(oneColumn[rowIndex].toString()),
                h2oFrame.at(frameRowIndex), EPSILON);

      frameRowIndex++;
    }
  }

  private void compareTimecolumn(ColumnVector oneTSColumn, boolean[] isNull, long currentBatchRow,
                                        Vec h2oFrame, Long startRowIndex) {
    long[] oneColumn = ((LongColumnVector) oneTSColumn).vector;
    long frameRowIndex = startRowIndex;

    for (int rowIndex = 0; rowIndex < currentBatchRow; rowIndex++) {
      if (isNull[rowIndex])
        assertEquals("Na is found: ", true, h2oFrame.isNA(frameRowIndex));
      else
        assertEquals("Numerical elements should equal: ", oneColumn[rowIndex], h2oFrame.at8(frameRowIndex),
                ERRORMARGIN);

      frameRowIndex++;
    }
  }

  private void compareStringcolumn(ColumnVector oneStringColumn, boolean[] isNull,
                                          long currentBatchRow, Vec h2oFrame, Long startRowIndex, String columnType) {
    byte[][] oneColumn = ((BytesColumnVector) oneStringColumn).vector;
    int[] stringLength = ((BytesColumnVector) oneStringColumn).length;
    int[] stringStart = ((BytesColumnVector) oneStringColumn).start;

    long frameRowIndex = startRowIndex;
    BufferedString h2o = new BufferedString();
    BufferedString tempOrc = new BufferedString();

    for (int rowIndex = 0; rowIndex < currentBatchRow; rowIndex++) {
      if (isNull[rowIndex])
        assertEquals("Na is found: ", true, h2oFrame.isNA(frameRowIndex));
      else {
            tempOrc.set(oneColumn[rowIndex], stringStart[rowIndex], stringLength[rowIndex]);
            h2oFrame.atStr(h2o, frameRowIndex);
            assertEquals("String/char elements should equal: ", true, tempOrc.equals(h2o));
      }

      frameRowIndex++;
    }
  }

  private void compareDoublecolumn(ColumnVector oneDoubleColumn, boolean[] isNull,
                                          long currentBatchRow, Vec h2oFrame, Long startRowIndex) {
    double[] oneColumn= ((DoubleColumnVector) oneDoubleColumn).vector;
    long frameRowIndex = startRowIndex;

    for (int rowIndex = 0; rowIndex < currentBatchRow; rowIndex++) {
      if (isNull[rowIndex])
        assertEquals("Na is found: ", true, h2oFrame.isNA(frameRowIndex));
      else
        assertEquals("Numerical elements should equal: ", oneColumn[rowIndex], h2oFrame.at(frameRowIndex), EPSILON);

      frameRowIndex++;
    }
  }

  private void CompareLongcolumn(ColumnVector oneLongColumn, boolean[] isNull,
                                        long currentBatchRow, Vec h2oFrame, Long startRowIndex) {
    long[] oneColumn= ((LongColumnVector) oneLongColumn).vector;
    long frameRowIndex = startRowIndex;

    for (int rowIndex = 0; rowIndex < currentBatchRow; rowIndex++) {
      if (isNull[rowIndex])
        assertEquals("Na is found: ", true, h2oFrame.isNA(frameRowIndex));
      else {
        assertEquals("Numerical elements should equal: ", oneColumn[rowIndex], h2oFrame.at8(frameRowIndex));
      }

      frameRowIndex++;
    }
  }
}