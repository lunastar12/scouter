/*
*  Copyright 2015 the original author or authors. 
 *  @https://github.com/scouter-project/scouter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package scouter.server.db;
import java.io.File
import scouter.lang.CounterKey
import scouter.lang.value.Value
import scouter.lang.value.ValueEnum
import scouter.server.Logger
import scouter.server.db.counter.DailyCounterData
import scouter.server.db.counter.DailyCounterIndex
import scouter.server.util.OftenAction
import scouter.server.util.ThreadScala
import scouter.util.FileUtil
import scouter.util.RequestQueue
object DailyCounterWR {
    val queue = new RequestQueue[Data](DBCtr.MAX_QUE_SIZE)
    val prefix = "5m";
    ThreadScala.start("scouter.server.db.DailyCounterWR") {
        while (DBCtr.running) {
            val m = queue.get();
            try {
                if (lastDateInt != m.date) {
                    lastDateInt = m.date;
                    close();
                    open(Integer.toString(m.date));
                }
                if (index == null) {
                    OftenAction.act("DailyCounterWR", 10) {
                        queue.clear();
                        lastDateInt=0;
                    }
                    Logger.println("S122", 10, "can't open db");
                } else {
                    val pkey = m.key.getBytesKey();
                    var location = index.get(pkey);
                    if (location >= 0) {
                        writer.write(location, m.key, m.hhmm, m.value);
                    } else {
                        if (m.value.getValueType() != ValueEnum.NULL) {
                            location = writer.write(m.key, m.hhmm, m.value);
                            index.set(pkey, location);
                        }
                    }
                }
            } catch {
                case t: Throwable => t.printStackTrace();
            }
        }
        close()
    }
    def add(date: Int, key: CounterKey, hhmm: Int, value: Value) {
        val ok = queue.put(new Data(date, key, hhmm, value));
        if (ok == false) {
            Logger.println("S123", 10, "queue exceeded!!");
        }
    }
    class Data(_date: Int, _key: CounterKey, _hhmm: Int, _value: Value) {
        val date = _date;
        val key = _key;
        val hhmm = _hhmm;
        val value = _value;
    }
    var lastDateInt: Int = 0
    var index: DailyCounterIndex = null
    var writer: DailyCounterData = null
    def close() {
        FileUtil.close(index);
        FileUtil.close(writer);
        index = null;
        writer = null;
    }
    def open(date: String) {
        try {
            val path = getDBPath(date);
            val f = new File(path);
            if (f.exists() == false)
                f.mkdirs();
            val file = path + "/" + prefix;
            index = DailyCounterIndex.open(file);
            writer = DailyCounterData.open(file);
            return ;
        } catch {
            case e: Throwable => {
                e.printStackTrace();
                close()
            }
        }
    }
    def getDBPath(date: String): String = {
        val sb = new StringBuffer();
        sb.append(DBCtr.getRootPath());
        sb.append("/").append(date).append("/counter");
        return sb.toString();
    }
}
