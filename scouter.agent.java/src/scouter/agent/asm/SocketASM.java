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

import java.util.HashMap;
import java.util.Map;

import scouter.agent.ClassDesc;
import scouter.agent.Configure;
import scouter.agent.asm.util.AsmUtil;
import scouter.agent.asm.util.HookingSet;
import scouter.agent.trace.TraceApiCall;
import scouter.org.objectweb.asm.ClassVisitor;
import scouter.org.objectweb.asm.Label;
import scouter.org.objectweb.asm.MethodVisitor;
import scouter.org.objectweb.asm.Opcodes;
import scouter.org.objectweb.asm.Type;
import scouter.org.objectweb.asm.commons.LocalVariablesSorter;

public class SocketASM implements IASM, Opcodes {
	private Map<String, HookingSet> reserved = new HashMap<String, HookingSet>();

	public SocketASM() {
			AsmUtil.add(reserved,"java/net/Socket","connect(Ljava/net/SocketAddress;I)V");
	}
	public boolean isTarget(String className) {
		return reserved.containsKey(className);
	}
	public ClassVisitor transform(ClassVisitor cv, String className, ClassDesc classDesc) {
		HookingSet mset = reserved.get(className);
		if (mset != null){			
			if(Configure.getInstance().enable_asm_socket==false)
				return cv;
			return new SocketCV(cv, mset, className);
		}
		return cv;
	}

}

class SocketCV extends ClassVisitor implements Opcodes {

	public String className;
	private HookingSet mset;

	public SocketCV(ClassVisitor cv, HookingSet mset, String className) {
		super(ASM4, cv);
		this.mset = mset;
		this.className = className;
	}

	@Override
	public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);		
		if (mv == null || mset.isA(methodName, desc) == false) {
			return mv;
		}
		if (AsmUtil.isSpecial(methodName)) {
			return mv;
		}
		
		return new SocketMV(access, desc, mv);
	}
}

// ///////////////////////////////////////////////////////////////////////////
class SocketMV extends LocalVariablesSorter implements Opcodes {
	private static final String TRACEAPICALL = TraceApiCall.class.getName().replace('.', '/');
	private final static String START_METHOD = "startSocket";
	private static final String START_SIGNATURE = "(Ljava/net/Socket;Ljava/net/SocketAddress;I)Ljava/lang/Object;";
	private final static String END_METHOD = "endSocket";
	private static final String END_SIGNATURE = "(Ljava/lang/Object;Ljava/lang/Throwable;)V";

	private Label startFinally = new Label();
	public SocketMV(int access, String desc, MethodVisitor mv) {
		super(ASM4,access, desc, mv);
	}

	
	private int statIdx;

	public void visitCode() {
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACEAPICALL, START_METHOD, START_SIGNATURE, false);

		statIdx = newLocal(Type.getType(Object.class));
		
		mv.visitVarInsn(Opcodes.ASTORE, statIdx);
		mv.visitLabel(startFinally);
		mv.visitCode();
	}


	@Override
	public void visitInsn(int opcode) {
		if ((opcode >= IRETURN && opcode <= RETURN)) {
			mv.visitVarInsn(Opcodes.ALOAD, statIdx);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACEAPICALL, END_METHOD, END_SIGNATURE, false);
		}
		mv.visitInsn(opcode);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		Label endFinally = new Label();
		mv.visitTryCatchBlock(startFinally, endFinally, endFinally, null);
		mv.visitLabel(endFinally);
		mv.visitInsn(DUP);
		int errIdx = newLocal(Type.getType(Throwable.class));
		mv.visitVarInsn(Opcodes.ASTORE, errIdx);

		mv.visitVarInsn(Opcodes.ALOAD, statIdx);
		mv.visitVarInsn(Opcodes.ALOAD, errIdx);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACEAPICALL, END_METHOD, END_SIGNATURE, false);
		mv.visitInsn(ATHROW);
		mv.visitMaxs(maxStack + 8, maxLocals + 2);
	}
}