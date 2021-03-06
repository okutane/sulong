/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.listeners.function;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.listeners.IRVersionController;
import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.listeners.Types;
import com.oracle.truffle.llvm.parser.listeners.ValueSymbolTable;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.generators.FunctionGenerator;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.records.FunctionRecord;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;

public abstract class Function implements ParserListener {

    private static final int INSERT_VALUE_MAX_ARGS = 3;

    private final IRVersionController version;

    private final FunctionGenerator generator;

    protected final Types types;

    protected final List<Type> symbols;

    private final int mode;

    protected InstructionBlock code;

    private final List<Integer> implicitIndices = new ArrayList<>();

    Function(IRVersionController version, Types types, List<Type> symbols, FunctionGenerator generator, int mode) {
        this.version = version;
        this.types = types;
        this.symbols = symbols;
        this.generator = generator;
        this.mode = mode;
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case CONSTANTS:
                return version.createConstants(types, symbols, generator);

            case VALUE_SYMTAB:
                return new ValueSymbolTable(generator);

            case METADATA:
                return version.createMetadata(types, symbols, generator); // TODO

            case METADATA_ATTACHMENT:
                return version.createMetadata(types, symbols, generator); // TODO

            default:
                LLVMLogger.info("Entering Unknown Block inside Function: " + block);
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        generator.exitFunction();
    }

    protected abstract void createAllocation(long[] args);

    protected abstract void createAtomicLoad(long[] args);

    protected abstract void createCall(long[] args);

    protected abstract void createInvoke(long[] args);

    protected abstract void createLandingpad(long[] args);

    protected abstract void createResume(long[] args);

    protected abstract void createLoad(long[] args);

    protected abstract void createSwitch(long[] args);

    @Override
    public void record(long id, long[] args) {
        FunctionRecord record = FunctionRecord.decode(id);

        if (record == FunctionRecord.DECLAREBLOCKS) {
            generator.allocateBlocks((int) args[0]);
            return;
        }

        /*
         * FUNC_CODE_DEBUG_LOC as well as FUNC_CODE_DEBUG_LOC_AGAIN also occur after the RET
         * Instruction, where the InstructionGenerator would already been deleted. This has to be
         * improved in the future, but for now we simply parse those instructions before checking
         * for an existing InstructionGenerator. Otherwise we would cause an RuntimeException.
         */
        if (record == FunctionRecord.DEBUG_LOC) {
            /*
             * TODO: implement intial debugging support
             *
             * http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html#format_common_lifetime
             * http://llvm.org/releases/3.4/docs/SourceLevelDebugging.html#object-lifetimes-and-scoping
             *
             * @formatter:off
             *
             * metadata !{
             *  i32 4,          ;; line number
             *  i32 0,          ;; column number
             *  metadata !12,   ;; scope
             *  null            ;; original scope
             * }
             *
             * @formatter:on
             */
            return;
        }

        if (record == FunctionRecord.DEBUG_LOC_AGAIN) {
            return;
        }

        if (code == null) {
            code = generator.generateBlock();
        }

        switch (record) {
            case BINOP:
                createBinaryOperation(args);
                break;

            case CAST:
                createCast(args);
                break;

            case GEP_OLD:
                createGetElementPointerOld(args, false);
                break;

            case EXTRACTELT:
                createExtractElement(args);
                break;

            case INSERTELT:
                createInsertElement(args);
                break;

            case SHUFFLEVEC:
                createShuffleVector(args);
                break;

            case RET:
                createReturn(args);
                break;

            case BR:
                createBranch(args);
                break;

            case SWITCH:
                createSwitch(args);
                break;

            case UNREACHABLE:
                createUnreachable(args);
                break;

            case PHI:
                createPhi(args);
                break;

            case ALLOCA:
                createAllocation(args);
                break;

            case LOAD:
                createLoad(args);
                break;

            case STORE_OLD:
                createStoreOld(args);
                break;

            case EXTRACTVAL:
                createExtractValue(args);
                break;

            case INSERTVAL:
                createInsertValue(args);
                break;

            case CMP2:
                createCompare2(args);
                break;

            case VSELECT:
                createSelect(args);
                break;

            case INBOUNDS_GEP_OLD:
                createGetElementPointerOld(args, true);
                break;

            case INDIRECTBR:
                createIndirectBranch(args);
                break;

            case CALL:
                createCall(args);
                break;

            case INVOKE:
                createInvoke(args);
                break;

            case LANDINGPAD:
                createLandingpad(args);
                break;

            case RESUME:
                createResume(args);
                break;

            case GEP:
                createGetElementPointer(args);
                break;

            case STORE:
                createStore(args);
                break;

            case LOADATOMIC:
                createAtomicLoad(args);
                break;

            case STOREATOMIC:
                createAtomicStore(args);
                break;

            case CMPXCHG_OLD:
            case CMPXCHG:
                createCompareExchange(args, record);
                break;

            default:
                throw new UnsupportedOperationException("Unsupported Record: " + record);
        }
    }

    private void createCompareExchange(long[] args, FunctionRecord record) {
        final Symbols functionSymbols = code.getFunctionSymbols();
        int i = 0;

        final Type ptrType;
        final int ptr = getIndex(args[i]);
        if (ptr >= functionSymbols.getSize()) {
            ptrType = types.get(args[++i]);
        } else {
            ptrType = symbols.get(ptr);
        }
        final int cmp = getIndex(args[++i]);
        if (record == FunctionRecord.CMPXCHG && cmp >= functionSymbols.getSize()) {
            ++i; // type of cmp
        }
        final int replace = getIndex(args[++i]);
        final boolean isVolatile = args[++i] != 0;
        final long successOrdering = args[++i];
        final long synchronizationScope = args[++i];
        final long failureOrdering = i < args.length - 1 ? args[++i] : -1L;
        final boolean addExtractValue = i >= args.length - 1;
        final boolean isWeak = addExtractValue || (args[++i] != 0);

        final Type type = findCmpxchgResultType(((PointerType) ptrType).getPointeeType());

        code.createCompareExchange(type, ptr, cmp, replace, isVolatile, successOrdering, synchronizationScope, failureOrdering, isWeak);
        symbols.add(type);

        if (addExtractValue) {
            // in older llvm versions cmpxchg just returned the new value at the pointer, to emulate
            // this we have to add an extractelvalue instruction. llvm does the same thing
            createExtractValue(new long[]{1, 0});
            implicitIndices.add(symbols.size() - 1); // register the implicit index
            LLVMLogger.info("cmpxchg implicitly inserted an extractelement instruction.");
        }
    }

    private static final int CMPXCHG_TYPE_LENGTH = 2;
    private static final int CMPXCHG_TYPE_ELEMENTTYPE = 0;
    private static final int CMPXCHG_TYPE_BOOLTYPE = 1;

    private Type findCmpxchgResultType(Type elementType) {
        // cmpxchg is the only instruction that does not directly reference its return type in the
        // type table
        for (Type t : types) {
            if (t != null && t instanceof StructureType) {
                final Type[] elts = ((StructureType) t).getElementTypes();
                if (elts.length == CMPXCHG_TYPE_LENGTH && elementType == elts[CMPXCHG_TYPE_ELEMENTTYPE] && PrimitiveType.I1 == elts[CMPXCHG_TYPE_BOOLTYPE]) {
                    return t;
                }
            }
        }
        // the type may not exist if the value is not being used
        return new StructureType(true, new Type[]{elementType, PrimitiveType.I1});
    }

    private void createAtomicStore(long[] args) {
        int i = 0;

        final int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        final int source = getIndex(args[i++]);
        if (source > symbols.size()) {
            i++;
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        code.createAtomicStore(destination, source, align, isVolatile, atomicOrdering, synchronizationScope);
    }

    private void createBinaryOperation(long[] args) {
        int i = 0;
        Type type;
        int lhs = getIndex(args[i++]);
        if (lhs < symbols.size()) {
            type = symbols.get(lhs);
        } else {
            type = types.get(args[i++]);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i++];
        int flags = i < args.length ? (int) args[i] : 0;

        code.createBinaryOperation(type, opcode, flags, lhs, rhs);

        symbols.add(type);
    }

    private void createBranch(long[] args) {
        if (args.length == 1) {
            code.createBranch((int) args[0]);
        } else {
            code.createBranch(getIndex(args[2]), (int) args[0], (int) args[1]);
        }

        code = null;
    }

    private void createCast(long[] args) {
        int i = 0;
        int value = getIndex(args[i++]);
        if (value >= symbols.size()) {
            i++;
        }
        Type type = types.get(args[i++]);
        int opcode = (int) args[i];

        code.createCast(type, opcode, value);

        symbols.add(type);
    }

    private void createCompare2(long[] args) {
        int i = 0;
        Type operandType;
        int lhs = getIndex(args[i++]);
        if (lhs < symbols.size()) {
            operandType = symbols.get(lhs);
        } else {
            operandType = types.get(args[i++]);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i];

        Type type = operandType instanceof VectorType
                        ? new VectorType(PrimitiveType.I1, ((VectorType) operandType).getNumberOfElements())
                        : PrimitiveType.I1;

        code.createCompare(type, opcode, lhs, rhs);

        symbols.add(type);
    }

    private void createExtractElement(long[] args) {
        int vector = getIndex(args[0]);
        int index = getIndex(args[1]);

        Type type = ((VectorType) symbols.get(vector)).getElementType();

        code.createExtractElement(type, vector, index);

        symbols.add(type);
    }

    private void createExtractValue(long[] args) {
        int aggregate = getIndex(args[0]);
        int index = (int) args[1];

        if (args.length != 2) {
            // This is supported in neither parser.
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        Type type = ((AggregateType) symbols.get(aggregate)).getElementType(index);

        code.createExtractValue(type, aggregate, index);

        symbols.add(type);
    }

    private void createGetElementPointer(long[] args) {
        int i = 0;
        boolean isInbounds = args[i++] != 0;
        i++; // we do not use this parameter
        int pointer = getIndex(args[i++]);
        Type base;
        if (pointer < symbols.size()) {
            base = symbols.get(pointer);
        } else {
            base = types.get(args[i++]);
        }
        int[] indices = getIndices(args, i);
        Type type = new PointerType(getElementPointerType(base, indices));

        code.createGetElementPointer(
                        type,
                        pointer,
                        indices,
                        isInbounds);

        symbols.add(type);
    }

    private void createGetElementPointerOld(long[] args, boolean isInbounds) {
        int i = 0;
        int pointer = getIndex(args[i++]);
        Type base;
        if (pointer < symbols.size()) {
            base = symbols.get(pointer);
        } else {
            base = types.get(args[i++]);
        }
        int[] indices = getIndices(args, i);

        Type type = new PointerType(getElementPointerType(base, indices));

        code.createGetElementPointer(
                        type,
                        pointer,
                        indices,
                        isInbounds);

        symbols.add(type);
    }

    private void createIndirectBranch(long[] args) {
        int address = getIndex(args[1]);
        int[] successors = new int[args.length - 2];
        for (int i = 0; i < successors.length; i++) {
            successors[i] = (int) args[i + 2];
        }

        code.createIndirectBranch(address, successors);

        code = null;
    }

    private void createInsertElement(long[] args) {
        int vector = getIndex(args[0]);
        int index = getIndex(args[2]);
        int value = getIndex(args[1]);

        Type symbol = symbols.get(vector);

        code.createInsertElement(symbol, vector, index, value);

        symbols.add(symbol);
    }

    private void createInsertValue(long[] args) {
        int aggregate = getIndex(args[0]);
        int index = (int) args[2];
        int value = getIndex(args[1]);

        if (args.length != INSERT_VALUE_MAX_ARGS) {
            // This is supported in neither parser.
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        Type symbol = symbols.get(aggregate);

        code.createInsertValue(symbol, aggregate, index, value);

        symbols.add(symbol);
    }

    private void createPhi(long[] args) {
        Type type = types.get(args[0]);
        int count = (args.length) - 1 >> 1;
        int[] values = new int[count];
        int[] blocks = new int[count];
        for (int i = 0, j = 1; i < count; i++) {
            values[i] = getIndex(Records.toSignedValue(args[j++]));
            blocks[i] = (int) args[j++];
        }

        code.createPhi(type, values, blocks);

        symbols.add(type);
    }

    private void createReturn(long[] args) {
        if (args.length == 0 || args[0] == 0) {
            code.createReturn();
        } else {
            code.createReturn(getIndex(args[0]));
        }

        code = null;
    }

    private void createSelect(long[] args) {
        int i = 0;
        Type type;
        int trueValue = getIndex(args[i++]);
        if (trueValue < symbols.size()) {
            type = symbols.get(trueValue);
        } else {
            type = types.get(args[i++]);
        }
        int falseValue = getIndex(args[i++]);
        int condition = getIndex(args[i]);

        code.createSelect(type, condition, trueValue, falseValue);

        symbols.add(type);
    }

    private void createShuffleVector(long[] args) {
        int vector1 = getIndex(args[0]);
        int vector2 = getIndex(args[1]);
        int mask = getIndex(args[2]);

        PrimitiveType subtype = ((VectorType) symbols.get(vector1)).getElementType();
        int length = ((VectorType) symbols.get(mask)).getNumberOfElements();
        Type type = new VectorType(subtype, length);

        code.createShuffleVector(type, vector1, vector2, mask);

        symbols.add(type);
    }

    private void createStore(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        int source = getIndex(args[i++]);
        if (source > symbols.size()) {
            i++;
        }

        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        code.createStore(destination, source, align, isVolatile);
    }

    private void createStoreOld(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        int source = getIndex(args[i++]);
        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        code.createStore(destination, source, align, isVolatile);
    }

    private void createUnreachable(@SuppressWarnings("unused") long[] args) {
        code.createUnreachable();

        code = null;
    }

    protected int getAlign(long argument) {
        return (int) argument & (Long.SIZE - 1);
    }

    private Type getElementPointerType(Type type, int[] indices) {
        Type elementType = type;
        for (int indice : indices) {
            if (elementType instanceof PointerType) {
                elementType = ((PointerType) elementType).getPointeeType();
            } else if (elementType instanceof ArrayType) {
                elementType = ((ArrayType) elementType).getElementType();
            } else if (elementType instanceof VectorType) {
                elementType = ((VectorType) elementType).getElementType();
            } else {
                StructureType structure = (StructureType) elementType;
                Type idx = symbols.get(indice);
                if (!(idx instanceof PrimitiveType)) {
                    throw new IllegalStateException("Cannot infer structure element from " + idx);
                }
                Number index = (Number) ((PrimitiveType) idx).getConstant();
                assert ((PrimitiveType) idx).getPrimitiveKind() == PrimitiveKind.I32;
                elementType = structure.getElementType(index.intValue());
            }
        }
        return elementType;
    }

    protected int getIndex(long index) {
        if (mode == 0) {
            return getIndexAbsolute(index);
        } else {
            return getIndexRelative(index);
        }
    }

    private int[] getIndices(long[] arguments, int from) {
        return getIndices(arguments, from, arguments.length);
    }

    private int[] getIndices(long[] arguments, int from, int to) {
        int[] indices = new int[to - from];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = getIndex(arguments[from + i]);
        }
        return indices;
    }

    protected int getIndexAbsolute(long index) {
        long actualIndex = index;
        for (int i = 0; i < implicitIndices.size() && implicitIndices.get(i) <= actualIndex; i++) {
            actualIndex++;
        }
        return (int) actualIndex;
    }

    protected int getIndexRelative(long index) {
        long actualIndex = symbols.size() - index;
        for (int i = implicitIndices.size() - 1; i >= 0 && implicitIndices.get(i) > actualIndex; i--) {
            actualIndex--;
        }
        return (int) actualIndex;
    }
}
