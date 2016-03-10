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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.sri.save.backend.JsonAction;

public class JsonAction_Test {
// [
//   {
//     "action": "Place",
//     "arguments": [ "Wall1Id", "Floor1Id", true ]
//   },
//   {
//     "action": "Install",
//     "arguments": [ "Door2Id", "Wall1Id", 2, 4 ]
//   }
// ]

    @Test
    public void readAction() {
        String input = "{\"action\": \"Place\", \"arguments\":"
                + " [ \"Wall1Id\", \"Floor1Id\", true ]}";
        Gson gson = new Gson();
        JsonAction act = gson.fromJson(input, JsonAction.class);
        Assert.assertEquals(act.action, "Place");
        Assert.assertEquals(act.arguments.length, 3);
        Assert.assertEquals(act.arguments[0], "Wall1Id");
        Assert.assertEquals(act.arguments[1], "Floor1Id");
        Assert.assertEquals(act.arguments[2], true);
    }
}
