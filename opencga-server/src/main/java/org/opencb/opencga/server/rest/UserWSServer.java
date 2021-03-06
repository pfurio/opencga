/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.server.rest;

import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.File;
import org.opencb.opencga.catalog.models.Project;
import org.opencb.opencga.catalog.models.Session;
import org.opencb.opencga.catalog.models.User;
import org.opencb.opencga.core.exception.VersionException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Path("/{version}/users")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Users", position = 1, description = "Methods for working with 'users' endpoint")
public class UserWSServer extends OpenCGAWSServer {


    public UserWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest) throws IOException, VersionException {
        super(uriInfo, httpServletRequest);
    }

    @GET
    @Path("/create")
    @Deprecated
    @ApiOperation(value = "Create a new user", response = User.class)
    public Response createUser(@ApiParam(value = "User id", required = true) @QueryParam("userId") String userId,
                               @ApiParam(value = "User name", required = true) @QueryParam("name") String name,
                               @ApiParam(value = "User's email", required = true) @QueryParam("email") String email,
                               @ApiParam(value = "User's password", required = true) @QueryParam("password") String password,
                               @ApiParam(value = "User's organization") @QueryParam("organization") String organization) {
        try {
            queryOptions.remove("password");
            QueryResult queryResult = catalogManager.createUser(userId, name, email, password, organization, null, queryOptions);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a new user", response = User.class)
    public Response createUserPost(@ApiParam(value = "JSON containing the parameters 'userId', 'name', 'email' and 'password' "
            + "and, optionally, 'organization'", required = true) Map<String, String> map) {
        try {
            if (!map.containsKey("userId") || !map.containsKey("name") || !map.containsKey("email")
                    || !map.containsKey("password")) {
                createErrorResponse(new CatalogException("userId, name, email or password not present"));
            }

            String userId = map.get("userId");
            String name = map.get("name");
            String email = map.get("email");
            String password = map.get("password");
            String organization = map.containsKey("organization") ? map.get("organization") : "";

            QueryResult queryResult = catalogManager.createUser(userId, name, email, password, organization, null, queryOptions);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/info")
    @ApiOperation(value = "Return the user information including its projects and studies", response = User.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Set which fields are included in the response, e.g.: name,alias...",
                    dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Set which fields are excluded in the response, e.g.: name,alias...",
                    dataType = "string", paramType = "query"),
    })
    public Response getInfo(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                            @ApiParam(value = "This parameter shows the last time the user information was modified. When "
                                    + "the value passed corresponds with the user's last activity registered, an empty result will be "
                                    + "returned meaning that the client already has the most up to date user information.", hidden = true)
                            @QueryParam ("lastModified") String lastModified) {
        try {
            QueryResult result = catalogManager.getUser(userId, lastModified, queryOptions, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @Deprecated
    @GET
    @Path("/{user}/login")
    @ApiOperation(value = "Get identified and gain access to the system [DEPRECATED]")
    public Response login(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                          @ApiParam(value = "User password", required = true) @QueryParam("password") String password) {
        sessionIp = httpServletRequest.getRemoteAddr();
        QueryResult<Session> queryResult;
        try {
            queryOptions.remove("password"); //Remove password from query options

            queryResult = catalogManager.login(userId, password, sessionIp);
            ObjectMap sessionMap = new ObjectMap();
            sessionMap.append("sessionId", queryResult.first().getId())
                    .append("id", queryResult.first().getId())
                    .append("ip", queryResult.first().getIp())
                    .append("date", queryResult.first().getDate());

            QueryResult<ObjectMap> login = new QueryResult<>("You successfully logged in", queryResult.getDbTime(), 1, 1,
                    queryResult.getWarningMsg(), queryResult.getErrorMsg(), Arrays.asList(sessionMap));

            return createOkResponse(login);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get identified and gain access to the systemn")
    public Response loginPost(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                              @ApiParam(value = "JSON containing the parameter 'password'", required = true) Map<String, String> map) {
        sessionIp = httpServletRequest.getRemoteAddr();
        QueryResult<Session> queryResult;
        try {
            if (!map.containsKey("password")) {
                throw new Exception("The json does not contain the key password.");
            }
            String password = map.get("password");
            queryResult = catalogManager.login(userId, password, sessionIp);

            ObjectMap sessionMap = new ObjectMap();
            sessionMap.append("sessionId", queryResult.first().getId())
                    .append("id", queryResult.first().getId())
                    .append("ip", queryResult.first().getIp())
                    .append("date", queryResult.first().getDate());

            QueryResult<ObjectMap> login = new QueryResult<>("You successfully logged in", queryResult.getDbTime(), 1, 1, queryResult
                    .getWarningMsg(), queryResult.getErrorMsg(), Arrays.asList(sessionMap));

            return createOkResponse(login);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/logout")
    @ApiOperation(value = "End user session")
    public Response logout(@ApiParam(value = "userId", required = true) @PathParam("user") String userId) {
        try {
            QueryResult result = catalogManager.logout(userId, sessionId);
            result.setId("You successfully logged out");
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/change-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Change the password of a user")
    public Response changePasswordPost(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                       @ApiParam(value = "JSON containing the params 'password' (old password) and 'npassword' (new "
                                               + "password)", required = true) ObjectMap params) {
        try {
            if (!params.containsKey("password") || !params.containsKey("npassword")) {
                throw new Exception("The json must contain the keys password and npassword.");
            }
            String password = params.getString("password");
            String nPassword = params.getString("npassword");
            QueryResult result = catalogManager.changePassword(userId, password, nPassword);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/reset-password")
    @ApiOperation(value = "Reset password", notes = "Reset the user's password and send a new random one to the e-mail stored in catalog.")
    public Response resetPassword(@ApiParam(value = "User id", required = true) @PathParam("user") String userId) {
        try {
            QueryResult result = catalogManager.getUserManager().resetPassword(userId, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/projects")
    @ApiOperation(value = "Retrieve the projects of the user", notes = "Retrieve the list of projects and studies belonging or shared with "
            + "the user", response = Project[].class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Set which fields are included in the response, e.g.: name,alias...",
                    dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Set which fields are excluded in the response, e.g.: name,alias...",
                    dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "Max number of results to be returned.", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "skip", value = "Number of results to be skipped.", dataType = "integer", paramType = "query")
    })
    public Response getAllProjects(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                   @ApiParam(value = "When false, it will return only the projects and studies belonging to the user. "
                                           + "However, if this parameter is set to true, only the projects and studies shared with the "
                                           + "user will be shown.", defaultValue = "false") @QueryParam ("shared") boolean shared) {
        try {
            QueryResult queryResult;
            if (!shared) {
                queryResult = catalogManager.getAllProjects(userId, queryOptions, sessionId);
            } else {
                queryResult = catalogManager.getProjectManager().getSharedProjects(userId, queryOptions, sessionId);
            }
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/update")
    @ApiOperation(value = "Update some user attributes", response = User.class)
    public Response update(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                           @ApiParam(value = "User name") @QueryParam("name") String name,
                           @ApiParam(value = "User's email") @QueryParam("email") String email,
                           @ApiParam(value = "User's organization") @QueryParam("organization") String organization,
                           @ApiParam(value = "JSON string containing additional information to be stored") @QueryParam("attributes")
                                       String attributes) {
        try {
            ObjectMap objectMap = new ObjectMap();
            objectMap.putIfNotNull("name", name);
            objectMap.putIfNotNull("email", email);
            objectMap.putIfNotNull("organization", organization);
            objectMap.putIfNotNull("attributes", attributes);

            QueryResult result = catalogManager.modifyUser(userId, objectMap, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update some user attributes", position = 9, response = User.class)
    public Response updateByPost(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                 @ApiParam(name = "params", value = "JSON containing the params to be updated. Supported keys " +
                                         "are: 'name', 'email', 'organization' and 'attributes'", required = true) ObjectMap params) {
        try {
            QueryResult result = catalogManager.modifyUser(userId, params, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/delete")
    @ApiOperation(value = "Delete a user [NOT TESTED]")
    public Response delete(@ApiParam(value = "Comma separated list of user ids", required = true) @PathParam("user") String userId) {
        try {
            List<QueryResult<User>> deletedUsers = catalogManager.getUserManager().delete(userId, queryOptions, sessionId);
            return createOkResponse(deletedUsers);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/create")
    @ApiOperation(value = "Store a user configuration", notes = "Some applications might want to store some configuration parameters "
            + "containing the preferences of the user. The intention of this is to provide a place to store this things for every user.",
            response = Map.class)
    public Response setConfiguration(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                         @ApiParam(value = "Unique name (typically the name of the application)", required = true)
                                         @QueryParam("name") String name,
                                         @ApiParam(name = "params", value = "JSON string containing anything useful for the application "
                                                 + "such as user or default preferences", required = true) String parameters) {
        try {
            return createOkResponse(catalogManager.getUserManager().setConfig(userId, sessionId, name, new ObjectMap(parameters)));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/configs/create")
    @ApiOperation(value = "Store a user configuration", notes = "Some applications might want to store some configuration parameters "
            + "containing the preferences of the user. The aim of this is to provide a place to store this things for every user.",
            response = Map.class)
    public Response setConfigurationPOST(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                    @ApiParam(value = "Unique name (typically the name of the application)", required = true) @QueryParam("name")
                            String name,
                    @ApiParam(name = "params", value = "JSON containing anything useful for the application such as user or default "
                            + "preferences", required = true) ObjectMap params) {
        try {
            return createOkResponse(catalogManager.getUserManager().setConfig(userId, sessionId, name, params));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/{name}/delete")
    @ApiOperation(value = "Delete a user configuration", response = Map.class)
    public Response deleteConfiguration(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                     @ApiParam(value = "Unique name (typically the name of the application)", required = true)
                                     @PathParam("name") String name) {
        try {
            return createOkResponse(catalogManager.getUserManager().deleteConfig(userId, sessionId, name));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/{name}/info")
    @ApiOperation(value = "Fetch a user configuration", response = Map.class)
    public Response getConfiguration(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                     @ApiParam(value = "Unique name (typically the name of the application)", required = true)
                                     @PathParam("name") String name) {
        try {
            return createOkResponse(catalogManager.getUserManager().getConfig(userId, sessionId, name));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/filters/create")
    @ApiOperation(value = "Store a custom filter", notes = "Users normally try to query the data using the same filters most of "
            + "the times. The aim of this WS is to allow storing as many different filters as the user might want in order not to type "
            + "the same filters.", response = User.Filter.class)
    public Response addFilter(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                              @ApiParam(value = "Name of the filter", required = true) @QueryParam("name") String name,
                              @ApiParam(value = "Bioformat for which the filters will make sense (generally VARIANT or ALIGNMENT). The "
                                      + "whole list of allowed bioformats can be checked in the files webservice /files/bioformats")
                                  @QueryParam("bioformat") String bioformatStr,
                              @ApiParam(value = "Description of the filter") @QueryParam("description") String description,
                              @ApiParam(value = "JSON string containing the query to be stored") @QueryParam("query") String queryStr,
                              @ApiParam(value = "JSON string containing modifiers of the result") @QueryParam("queryOptions")
                                          String queryOptionsStr) {

        File.Bioformat bioformat = File.Bioformat.UNKNOWN;
        if (StringUtils.isNotEmpty(bioformatStr)) {
            try {
                bioformat = File.Bioformat.valueOf(bioformatStr.toUpperCase());
            } catch (Exception e) {
                return createErrorResponse(new CatalogException("Bioformat " + bioformatStr + " is not a valid bioformat."));
            }
        }

        try {
            Query myQuery;
            QueryOptions myOptions;

            if (StringUtils.isNotEmpty(queryStr)) {
                myQuery = new Query(queryStr);
            } else {
                myQuery = new Query();
            }

            if (StringUtils.isNotEmpty(queryOptionsStr)) {
                myOptions = new QueryOptions(queryOptionsStr);
            } else {
                myOptions = new QueryOptions();
            }

            return createOkResponse(catalogManager.getUserManager().addFilter(userId, sessionId, name, description, bioformat, myQuery,
                    myOptions));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/configs/filters/create")
    @ApiOperation(value = "Store a custom filter", notes = "Users normally try to query the data using the same filters most of "
            + "the times. The aim of this WS is to allow storing as many different filters as the user might want in order not to type "
            + "the same filters.", response = User.Filter.class)
    public Response addFilterPOST(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                           @ApiParam(name = "params", value = "Filter parameters", required = true) User.Filter params) {
        try {
            return createOkResponse(catalogManager.getUserManager().addFilter(userId, sessionId, params.getName(), params.getDescription(),
                    params.getBioformat(), params.getQuery(), params.getOptions()));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    private static class UpdateFilter {
        public File.Bioformat bioformat;
        public String description;
        public Query query;
        public QueryOptions options;
    }

    @GET
    @Path("/{user}/configs/filters/{name}/update")
    @ApiOperation(value = "Update a custom filter", response = User.Filter.class)
    public Response updateFilter(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                 @ApiParam(value = "Filter name", required = true) @PathParam("name") String name,
                                 @ApiParam(value = "Bioformat for which the filters will make sense (generally VARIANT or ALIGNMENT). The "
                                         + "whole list of allowed bioformats can be checked in the files webservice /files/bioformats")
                                     @QueryParam("bioformat") String bioformatStr,
                                 @ApiParam(value = "Description of the filter") @QueryParam("description") String description,
                                 @ApiParam(value = "JSON string containing the query to be stored") @QueryParam("query") String queryStr,
                                 @ApiParam(value = "JSON string containing modifiers of the result") @QueryParam("queryOptions")
                                             String queryOptionsStr) {
        try {
            ObjectMap params = new ObjectMap();
            params.putIfNotEmpty("bioformat", bioformatStr);
            params.putIfNotEmpty("description", description);
            if (StringUtils.isNotEmpty(queryStr)) {
                params.put("query", new Query(queryStr));
            }
            if (StringUtils.isNotEmpty(queryOptionsStr)) {
                params.put("options", new QueryOptions(queryOptionsStr));
            }

            return createOkResponse(catalogManager.getUserManager().updateFilter(userId, sessionId, name, params));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{user}/configs/filters/{name}/update")
    @ApiOperation(value = "Update a custom filter", response = User.Filter.class)
    public Response updateFilterPOST(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                              @ApiParam(value = "Filter name", required = true) @PathParam("name") String name,
                              @ApiParam(name = "params", value = "Filter parameters", required = true) UpdateFilter params) {
        try {
            return createOkResponse(catalogManager.getUserManager().updateFilter(userId, sessionId, name,
                    new ObjectMap(jsonObjectMapper.writeValueAsString(params))));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/filters/{name}/delete")
    @ApiOperation(value = "Delete a custom filter", response = User.Filter.class)
    public Response deleteFilter(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                 @ApiParam(value = "Filter name", required = true) @PathParam("name") String name) {
        try {
            return createOkResponse(catalogManager.getUserManager().deleteFilter(userId, sessionId, name));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/filters/{name}/info")
    @ApiOperation(value = "Fetch a filter", response = User.Filter.class)
    public Response getFilter(@ApiParam(value = "User id", required = true) @PathParam("user") String userId,
                                 @ApiParam(value = "Filter name", required = true) @PathParam("name") String name) {
        try {
            return createOkResponse(catalogManager.getUserManager().getFilter(userId, sessionId, name));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{user}/configs/filters/list")
    @ApiOperation(value = "Fetch all the filters of a user", response = User.Filter.class)
    public Response getFilters(@ApiParam(value = "User id", required = true) @PathParam("user") String userId) {
        try {
            return createOkResponse(catalogManager.getUserManager().getAllFilters(userId, sessionId));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

}