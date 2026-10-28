package com.example.dbagg.controller;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.CompareRequest;
import com.example.dbagg.model.CreateInstanceRequest;
import com.example.dbagg.model.DbKind;
import com.example.dbagg.model.DiffResult;
import com.example.dbagg.model.Instance;
import com.example.dbagg.model.UpdatePasswordRequest;
import com.example.dbagg.repository.CatalogRepository;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.service.CacheProvider;
import com.example.dbagg.service.CatalogEventBus;
import com.example.dbagg.service.ConnectionUrlValidator;
import com.example.dbagg.service.DiffService;
import com.example.dbagg.service.SchemaLoader;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.reactive.RestSseElementType;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API управления инстансами, сборки схем и сравнения каталогов.
 *
 * <p>Все ответы — JSON. SSE-поток {@code GET /instances/{id}/events} отдаёт собранные каталоги
 * инстанса по мере их публикации в {@link CatalogEventBus}.
 */
@ApplicationScoped
@Path("/instances")
@Produces(MediaType.APPLICATION_JSON)
public class DbController {
  private static final Logger log = LoggerFactory.getLogger(DbController.class);

  private final InstanceRepository instanceRepository;
  private final CatalogRepository catalogRepository;
  private final SchemaLoader schemaLoader;
  private final CacheProvider cacheProvider;
  private final DiffService diffService;
  private final CatalogEventBus catalogEventBus;
  private final ConnectionUrlValidator connectionUrlValidator;

  @Inject
  public DbController(
      InstanceRepository instanceRepository,
      CatalogRepository catalogRepository,
      SchemaLoader schemaLoader,
      CacheProvider cacheProvider,
      DiffService diffService,
      CatalogEventBus catalogEventBus,
      ConnectionUrlValidator connectionUrlValidator) {
    this.instanceRepository = instanceRepository;
    this.catalogRepository = catalogRepository;
    this.schemaLoader = schemaLoader;
    this.cacheProvider = cacheProvider;
    this.diffService = diffService;
    this.catalogEventBus = catalogEventBus;
    this.connectionUrlValidator = connectionUrlValidator;
  }

  @GET
  public List<Instance> listInstances() {
    return instanceRepository.findAllInstances();
  }

  @GET
  @Path("/{id}")
  public Instance getInstance(@PathParam("id") long id) {
    return instanceRepository
        .findInstanceById(id)
        .orElseThrow(() -> new NotFoundException("Instance not found: " + id));
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createInstance(@Valid CreateInstanceRequest request) {
    connectionUrlValidator.validate(request.connectionUrl(), request.kind());
    var instance =
        new Instance(
            0,
            request.label(),
            request.connectionUrl(),
            request.username(),
            DbKind.fromCode(request.kind()).name());
    var id = instanceRepository.saveInstance(instance, request.password());
    var saved = instanceRepository.findInstanceById(id).orElseThrow();
    log.info("Instance created: {} (id={})", saved.label(), id);
    return Response.status(Response.Status.CREATED).entity(saved).build();
  }

  @PATCH
  @Path("/{id}/password")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updatePassword(@PathParam("id") long id, @Valid UpdatePasswordRequest request) {
    requireInstance(id);
    instanceRepository.storeEncryptedPassword(id, request.password());
    log.info("Password updated for instance {}", id);
    return Response.noContent().build();
  }

  @POST
  @Path("/{id}/load")
  public Catalog loadSchema(@PathParam("id") long id) {
    requireInstance(id);
    var catalog = schemaLoader.load(id);
    catalogEventBus.publish(id, catalog);
    log.info("Schema loaded for instance {}", id);
    return catalog;
  }

  /**
   * Контроль изменений (drift): сравнивает сохранённый эталон схемы инстанса с её текущим
   * состоянием, собранным вживую из целевой БД. Эталон не меняется.
   */
  @POST
  @Path("/{id}/check")
  public DiffResult checkChanges(@PathParam("id") long id) {
    requireInstance(id);
    var baseline = cacheProvider.get(id).orElseGet(() -> catalogRepository.loadCatalog(id));
    var live = schemaLoader.collectLive(id);
    log.info("Drift check for instance {}: baseline vs current", id);
    return diffService.compare(baseline, live);
  }

  @GET
  @Path("/{id}/schema")
  public Catalog getSchema(@PathParam("id") long id) {
    requireInstance(id);
    var fromCache = cacheProvider.get(id);
    if (fromCache.isPresent()) {
      return fromCache.get();
    }
    return catalogRepository.loadCatalog(id);
  }

  @POST
  @Path("/compare")
  @Consumes(MediaType.APPLICATION_JSON)
  public DiffResult compare(@Valid CompareRequest request) {
    requireInstance(request.baseInstanceId());
    requireInstance(request.compareInstanceId());
    return diffService.compare(request.baseInstanceId(), request.compareInstanceId());
  }

  @GET
  @Path("/{id}/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestSseElementType(MediaType.APPLICATION_JSON)
  public Multi<Catalog> events(@PathParam("id") long id) {
    requireInstance(id);
    return catalogEventBus.stream(id);
  }

  private void requireInstance(long id) {
    if (instanceRepository.findInstanceById(id).isEmpty()) {
      throw new NotFoundException("Instance not found: " + id);
    }
  }

  @ServerExceptionMapper
  public Response mapIllegalArgument(IllegalArgumentException e) {
    return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
  }
}
