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
package com.oracle.truffle.llvm.runtime;

import java.io.IOException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(name = "Sulong", version = "0.01", mimeType = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE,
                LLVMLanguage.SULONG_LIBRARY_MIME_TYPE})
public final class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    /*
     * The LLVM class has static initializers with side effects that we rely on, but we have no
     * dependency on it here, and no way to statically reference it even.
     */

    static {
        try {
            Class.forName("com.oracle.truffle.llvm.LLVM", true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    public static final String LLVM_BITCODE_EXTENSION = "bc";

    /*
     * The Truffle source API does not handle binary data well - it will read binary files in as
     * strings in an unknown encoding. To get around this until it is fixed, we store binary data in
     * base 64 strings when they don't exist as a file which can be read directly.
     */
    public static final String LLVM_BITCODE_BASE64_MIME_TYPE = "application/x-llvm-ir-bitcode-base64";

    public static final String SULONG_LIBRARY_MIME_TYPE = "application/x-sulong-library";
    public static final String SULONG_LIBRARY_EXTENSION = "su";

    public LLVMLanguage() {

    }

    public interface LLVMLanguageProvider {
        LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env);

        CallTarget parse(LLVMLanguage language, LLVMContext context, Source code, String... argumentNames) throws IOException;

        void disposeContext(LLVMContext context);
    }

    public static LLVMLanguageProvider provider;

    public static final String MAIN_ARGS_KEY = "Sulong Main Args";
    public static final String LLVM_SOURCE_FILE_KEY = "Sulong Source File";
    public static final String PARSE_ONLY_KEY = "Parse only";

    private com.oracle.truffle.api.TruffleLanguage.Env environment;

    @Override
    protected LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        this.environment = env;
        return provider.createContext(env);
    }

    public com.oracle.truffle.api.TruffleLanguage.Env getEnvironment() {
        return environment;
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        context.printNativeCallStatistic();
        provider.disposeContext(context);
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return provider.parse(this, findLLVMContext(), source, request.getArgumentNames().toArray(new String[request.getArgumentNames().size()]));
    }

    @Override
    protected Object findExportedSymbol(LLVMContext context, String globalName, boolean onlyExplicit) {
        String atname = "@" + globalName; // for interop
        for (LLVMFunctionDescriptor descr : context.getFunctionDescriptors()) {
            if (descr != null && descr.getName().equals(globalName)) {
                return descr;
            } else if (descr != null && descr.getName().equals(atname)) {
                return descr;
            }
        }
        return null;
    }

    @Override
    protected Object getLanguageGlobal(LLVMContext context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        throw new AssertionError();
    }

    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

}
