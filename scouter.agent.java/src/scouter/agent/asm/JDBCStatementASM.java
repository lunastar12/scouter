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
 */
package scouter.agent.asm;
import java.util.HashSet;
import scouter.agent.ClassDesc;
import scouter.agent.Configure;
import scouter.agent.Logger;
import scouter.agent.asm.jdbc.StExecuteMV;
import scouter.agent.asm.util.HookingSet;
import scouter.org.objectweb.asm.ClassVisitor;
import scouter.org.objectweb.asm.MethodVisitor;
import scouter.org.objectweb.asm.Opcodes;
public class JDBCStatementASM implements IASM, Opcodes {
	public final HashSet<String> target =  HookingSet.getHookingClassSet(Configure.getInstance().hook_jdbc_stmt);
	public JDBCStatementASM() {
	
		target.add("org/mariadb/jdbc/MySQLStatement");
		target.add("oracle/jdbc/driver/OracleStatement");
		target.add("com/mysql/jdbc/StatementImpl");
		target.add("org/apache/derby/client/am/Statement");
		target.add("jdbc/FakeStatement");
		target.add("net/sourceforge/jtds/jdbc/JtdsStatement");
		target.add("com/microsoft/sqlserver/jdbc/SQLServerStatement");
		target.add("com/tmax/tibero/jdbc/TbStatement");
		target.add("org/hsqldb/jdbc/JDBCStatement");
	}
	public boolean isTarget(String className) {
		return target.contains(className) ;
	}
	public ClassVisitor transform(ClassVisitor cv, String className, ClassDesc classDesc) {
		if (target.contains(className) == false) {
			return cv;
		}
		if(Configure.getInstance().enable_asm_jdbc==false)
			return cv;
		Logger.println("A108", "jdbc stmt found: " + className);
		return new StatementCV(cv);
	}
}
class StatementCV extends ClassVisitor implements Opcodes {
	private String owner;
	public StatementCV(ClassVisitor cv) {
		super(ASM4, cv);
	}
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.owner = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		if (StExecuteMV.isTarget(name)) {
			if (desc.startsWith("(Ljava/lang/String;)")) {
				return new StExecuteMV(access, desc, mv, owner);
			}
		}
		return mv;
	}
}
