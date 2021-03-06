/**
 * Copyright 2013 Lukas Nalezenec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package parquet.proto;

import com.google.protobuf.Message;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.junit.Test;
import parquet.Log;
import parquet.proto.utils.ReadUsingMR;
import parquet.proto.utils.WriteUsingMR;
import parquet.protobuf.test.TestProtobuf;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class ProtoInputOutputFormatTest {

  private static final Log LOG = Log.getLog(ProtoInputOutputFormatTest.class);

  /**
   * Writes protobuffer using first MR job, reads written file using
   * second job and compares input and output.
   */
  @Test
  public void testInputOutput() throws Exception {
    TestProtobuf.IOFormatMessage input;
    {
      TestProtobuf.IOFormatMessage.Builder msg = TestProtobuf.IOFormatMessage.newBuilder();
      msg.setOptionalDouble(666);
      msg.addRepeatedString("Msg1");
      msg.addRepeatedString("Msg2");
      msg.getMsgBuilder().setSomeId(323);
      input = msg.build();
    }

    List<Message> result = runMRJobs(TestProtobuf.IOFormatMessage.class, input);

    assertEquals(1, result.size());
    TestProtobuf.IOFormatMessage output = (TestProtobuf.IOFormatMessage) result.get(0);

    assertEquals(666, output.getOptionalDouble(), 0.00001);
    assertEquals(323, output.getMsg().getSomeId());
    assertEquals("Msg1", output.getRepeatedString(0));
    assertEquals("Msg2", output.getRepeatedString(1));

    assertEquals(input, output);

  }


  @Test
  public void testProjection() throws Exception {

    TestProtobuf.Document.Builder writtenDocument = TestProtobuf.Document.newBuilder();
    writtenDocument.setDocId(12345);
    writtenDocument.addNameBuilder().setUrl("http://goout.cz/");

    Path outputPath = new WriteUsingMR().write(TestProtobuf.Document.class, writtenDocument.build());

    //lets prepare reading with schema
    ReadUsingMR reader = new ReadUsingMR();

    Configuration conf = new Configuration();
    reader.setConfiguration(conf);
    String projection = "message Document {required int64 DocId; }";
    ProtoParquetInputFormat.setRequestedProjection(conf, projection);

    List<Message> output = reader.read(outputPath);
    TestProtobuf.Document readDocument = (TestProtobuf.Document) output.get(0);


    //test that only requested fields were deserialized
    assertTrue(readDocument.hasDocId());
    assertTrue("Found data outside projection.", readDocument.getNameCount() == 0);
  }

  /**
   * Runs job that writes input to file and then job reading data back.
   */
  public static List<Message> runMRJobs(Class<? extends Message> pbClass, Message... messages) throws Exception {
    Path outputPath = new WriteUsingMR().write(pbClass, messages);
    List<Message> result = new ReadUsingMR().read(outputPath);
    return result;
  }
}
