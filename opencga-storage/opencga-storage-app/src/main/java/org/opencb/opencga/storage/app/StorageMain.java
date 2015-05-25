/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.app;

import org.opencb.opencga.storage.app.cli.*;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by imedina on 02/03/15.
 */
public class StorageMain {

    public static final String VERSION = "0.6.0-SNAPSHOT";

    public static void main(String[] args) {

        CliOptionsParser cliOptionsParser = new CliOptionsParser();
        cliOptionsParser.parse(args);

        String parsedCommand = cliOptionsParser.getCommand();
        if(parsedCommand == null || parsedCommand.isEmpty()) {
            if(cliOptionsParser.getGeneralOptions().help) {
                cliOptionsParser.printUsage();
            }
            if(cliOptionsParser.getGeneralOptions().version) {
                System.out.println("Version " + VERSION);
            }
        }else {
            CommandExecutor commandExecutor = null;
            switch (parsedCommand) {
                case "create-accessions":
                    if (cliOptionsParser.getCreateAccessionsCommandOption().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new CreateAccessionsCommandExecutor(cliOptionsParser.getCreateAccessionsCommandOption());
                    }
                    break;
                case "index-alignments":
                    if (cliOptionsParser.getIndexAlignmentsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new IndexAlignmentsCommandExecutor(cliOptionsParser.getIndexAlignmentsCommandOptions());
                    }
                    break;
                case "index-variants":
                    if (cliOptionsParser.getIndexVariantsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new IndexVariantsCommandExecutor(cliOptionsParser.getIndexVariantsCommandOptions());
                    }
                    break;
                case "fetch-alignments":
                    if (cliOptionsParser.getQueryAlignmentsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new FetchAlignmentsCommandExecutor(cliOptionsParser.getQueryAlignmentsCommandOptions());
                    }
                    break;
                case "fetch-variants":
                    if (cliOptionsParser.getQueryVariantsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new FetchVariantsCommandExecutor(cliOptionsParser.getQueryVariantsCommandOptions());
                    }
                    break;
                case "annotate-variants":
                    if (cliOptionsParser.getAnnotateVariantsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new AnnotateVariantsCommandExecutor(cliOptionsParser.getAnnotateVariantsCommandOptions());
                    }
                    break;
                case "stats-variants":
                    if (cliOptionsParser.getStatsVariantsCommandOptions().help) {
                        cliOptionsParser.printUsage();
                    } else {
                        commandExecutor = new StatsVariantsCommandExecutor(cliOptionsParser.getStatsVariantsCommandOptions());
                    }
                    break;
                default:
                    break;
            }

            if (commandExecutor != null) {
                try {
                    commandExecutor.loadOpenCGAStorageConfiguration();
                    commandExecutor.execute();
                } catch (IOException |URISyntaxException ex) {
                    commandExecutor.getLogger().error("Error reading OpenCGA Storage configuration: " + ex.getMessage());
                }
            }
        }
    }


}