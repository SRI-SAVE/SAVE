/*
 * Copyright 2016 SRI International
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

package com.sri.save.backend;

import java.io.InputStream;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sri.save.backend.XApiRequestTracker.XApiStatement;

public class XApiRequestTracker_Test {
    /**
     * Make sure we can read a JSON xAPI statement, of the form we'll receive
     * from PERLS, and not mangle the data in it. Also test the sort of
     * statement we'll send as a response.
     */
    @Test(dataProvider = "xAPI JSON")
    public void roundTrip(String orig)
            throws Exception {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
                .create();

        XApiStatement stmt = gson.fromJson(orig, XApiStatement.class);

        String proc = gson.toJson(stmt);

        orig = orig.replaceAll("[\r\n]+", "\n");
        proc = proc.replaceAll("[\r\n]+", "\n");

        Assert.assertEquals(proc, orig);
    }

    @DataProvider(name = "xAPI JSON")
    public Object[][] getXApiStrings()
            throws Exception {
        String[] names = new String[] { "PERLS_request.json",
                "SAVE_response.json" };
        Object[][] results = new Object[names.length][1];
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            try (InputStream is = getClass().getResourceAsStream(name)) {
                StringBuilder buf = new StringBuilder();
                while (is.available() > 0) {
                    buf.append((char) is.read());
                }
                results[i][0] = buf.toString();
            }
        }
        return results;
    }
}
