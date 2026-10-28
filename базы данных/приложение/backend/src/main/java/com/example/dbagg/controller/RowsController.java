package com.example.dbagg.controller;

import com.example.dbagg.model.RowGrid;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.service.RowsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** REST API эксплорера строк таблиц: живая постраничная выборка для DataGrid. */
@ApplicationScoped
@Path("/instances/{id}/tables/{schema}/{table}/rows")
@Produces(MediaType.APPLICATION_JSON)
public class RowsController {
  private final InstanceRepository instanceRepository;
  private final RowsService rowsService;

  @Inject
  public RowsController(InstanceRepository instanceRepository, RowsService rowsService) {
    this.instanceRepository = instanceRepository;
    this.rowsService = rowsService;
  }

  /** Живая выборка строк таблицы для DataGrid. */
  @GET
  public RowGrid rows(
      @PathParam("id") long id,
      @PathParam("schema") String schema,
      @PathParam("table") String table,
      @QueryParam("limit") int limit,
      @QueryParam("offset") int offset) {
    requireInstance(id);
    return rowsService.liveRows(id, schema, table, limit, offset);
  }

  private void requireInstance(long id) {
    if (instanceRepository.findInstanceById(id).isEmpty()) {
      throw new NotFoundException("Instance not found: " + id);
    }
  }
}
