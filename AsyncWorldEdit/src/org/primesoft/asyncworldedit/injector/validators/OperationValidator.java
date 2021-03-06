/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * Copyright (c) 2014, SBPrime <https://github.com/SBPrime/>
 * Copyright (c) AsyncWorldEdit contributors
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted free of charge provided that the following 
 * conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution,
 * 3. Redistributions of source code, with or without modification, in any form 
 *    other then free of charge is not allowed,
 * 4. Redistributions in binary form in any form other then free of charge is 
 *    not allowed.
 * 5. Any derived work based on or containing parts of this software must reproduce 
 *    the above copyright notice, this list of conditions and the following 
 *    disclaimer in the documentation and/or other materials provided with the 
 *    derived work.
 * 6. The original author of the software is allowed to change the license 
 *    terms or the entire license of the software as he sees fit.
 * 7. The original author of the software is allowed to sublicense the software 
 *    or its parts using any license terms he sees fit.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.primesoft.asyncworldedit.injector.validators;

import com.sk89q.worldedit.function.operation.Operation;
import java.util.regex.Pattern;
import static org.primesoft.asyncworldedit.AsyncWorldEditBukkit.log;
import org.primesoft.asyncworldedit.configuration.ConfigProvider;

/**
 * Validate if operation should by asynced
 *
 * @author SBPrime
 */
public class OperationValidator {

    /**
     * The blacklisted operations regexp
     */
    private static final Pattern[] s_blackList;

    /**
     * The whitelisted operations regexp
     */
    private static final Pattern[] s_whiteList;

    static {
        //No operations are on the black list (for now!)
        s_blackList = new Pattern[]{};

        s_whiteList = new Pattern[]{
            Pattern.compile(".*") //All operations are on the whitelist
        };
    }

    /**
     * Is the operation enabled for asyncing
     *
     * @param op
     * @return
     */
    public static boolean isValid(Operation op) {
        boolean debugOn = ConfigProvider.isDebugOn();
        Class c = op.getClass();
        String className = c.getCanonicalName();

        if (debugOn) {
            log("****************************************************************");
            log("* Validating operation");
            log("****************************************************************");
        }
        try {
            for (Pattern p : s_blackList) {
                if (p.matcher(className).matches()) {
                    if (debugOn) {
                        log("*");
                        log("* Found on blacklist");
                        log("* Opeation:\t" + className);
                        log("* Pattern:\t" + p.pattern());
                    }
                    return false;
                }
            }

            for (Pattern p : s_whiteList) {
                if (p.matcher(className).matches()) {
                    if (debugOn) {
                        log("*");
                        log("* Found on whitelist");
                        log("* Opeation:\t" + className);
                        log("* Pattern:\t" + p.pattern());
                    }
                    return true;
                }
            }

            if (debugOn) {
                log("*");
                log("* No match found");
            }
            return false;
        } finally {
            if (debugOn) {
                log("****************************************************************");
            }
        }
    }
}
