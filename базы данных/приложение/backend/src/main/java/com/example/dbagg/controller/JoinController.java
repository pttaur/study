package com.example.dbagg.controller;

import com.example.dbagg.model.JoinRequest;
import com.example.dbagg.model.QueryHistoryEntry;
import com.example.dbagg.model.QueryRequest;
import com.example.dbagg.model.QueryResult;
import com.example.dbagg.service.FederationService;
import com.example.dbagg.service.JoinSqlBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * REST API конструктора джойнов: сборка модели в SQL и исполнение федеративного запроса.
 *
 * <p>Произвольный SQL наружу не выставляется — только структурированное описание соединения.
 */
@ApplicationScoped
@Path("/join")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JoinController {
  private final JoinSqlBuilder joinSqlBuilder;
  private final FederationService federationService;

  @Inject
  public JoinController(JoinSqlBuilder joinSqlBuilder, FederationService federationService) {
    this.joinSqlBuilder = joinSqlBuilder;
    this.federationService = federationService;
  }

  @POST
  public QueryResult join(@Valid JoinRequest request) {
    var sql = joinSqlBuilder.build(request);
    return federationService.execute(
        new QueryRequest(sql, request.maxRows(), request.offset(), request.timeoutMs()));
  }

  @GET
  @Path("/history")
  public List<QueryHistoryEntry> history() {
    return federationService.history();
  }

  @ServerExceptionMapper
  public Response mapIllegalArgument(IllegalArgumentException e) {
    return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
  }
}
