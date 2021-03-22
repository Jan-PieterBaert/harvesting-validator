package mu.semte.ch.harvesting.filtering.lib.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import mu.semte.ch.harvesting.filtering.lib.dto.DataContainer;
import mu.semte.ch.harvesting.filtering.lib.dto.Task;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static mu.semte.ch.harvesting.filtering.lib.Constants.ERROR_URI_PREFIX;
import static mu.semte.ch.harvesting.filtering.lib.Constants.LOGICAL_FILE_PREFIX;
import static mu.semte.ch.harvesting.filtering.lib.utils.ModelUtils.formattedDate;
import static mu.semte.ch.harvesting.filtering.lib.utils.ModelUtils.uuid;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class TaskHelper {

  private String shareFolderPath;
  private SparqlQueryStore queryStore;
  private SparqlClient sparqlClient;

  public boolean isTask(String subject) {
    String queryStr = queryStore.getQuery("isTask").formatted(subject);

    return sparqlClient.executeAskQuery(queryStr);
  }

  public Task loadTask(String deltaEntry) {
    String queryTask = queryStore.getQuery("loadTask").formatted(deltaEntry);

    return sparqlClient.executeSelectQuery(queryTask, resultSet -> {
      if (!resultSet.hasNext()) {
        return null;
      }
      var t = resultSet.next();
      Task task = Task.builder().task(t.getResource("task").getURI())
                      .job(t.getResource("job").getURI())
                      .error(ofNullable(t.getResource("error")).map(Resource::getURI).orElse(null))
                      .id(t.getLiteral("id").getString())
                      .created(t.getLiteral("created").getString())
                      .modified(t.getLiteral("modified").getString())
                      .operation(t.getResource("operation").getURI())
                      .index(t.getLiteral("index").getString())
                      .graph(t.getResource("graph").getURI())
                      .status(t.getResource("status").getURI())
                      .build();
      log.debug("task: {}", task);
      return task;
    });

  }

  public Model loadImportedTriples(String graphImportedTriples) {
    String queryTask = queryStore.getQuery("loadImportedTriples").formatted(graphImportedTriples);
    return sparqlClient.executeConstructQueryResultAsSparqlJson(queryTask);
  }

  public void updateTaskStatus(Task task, String status) {
    String queryUpdate = queryStore.getQuery("updateTaskStatus")
                                   .formatted(status, formattedDate(LocalDateTime.now()), task.getTask());
    sparqlClient.executeUpdateQuery(queryUpdate);
  }

  public void importTriples(String graph,
                            Model model,
                            int batchSize) {
    log.debug("running import triples with batch size {}, model size: {}, graph: <{}>", batchSize, model.size(), graph);
    List<Triple> triples = model.getGraph().find().toList(); //duplicate so we can splice
    var batches = Lists.partition(triples, batchSize)
                       .stream()
                       .map(batch -> {
                         Model batchModel = ModelFactory.createDefaultModel();
                         Graph batchGraph = batchModel.getGraph();
                         batch.forEach(batchGraph::add);
                         return batchModel;
                       })
                       .collect(Collectors.toList());
    for (var batchModel : batches) {
        sparqlClient.insertModel(graph, batchModel);
    }
  }

  @SneakyThrows
  public String writeTtlFile(String graph,
                             Model content,
                             String logicalFileName) {
    var phyId = uuid();
    var phyFilename = "%s.nt".formatted(phyId);
    var path = "%s/%s".formatted(shareFolderPath, phyFilename);
    var physicalFile = "share://%s".formatted(phyFilename);
    var loId = uuid();
    var logicalFile = "%s/%s".formatted(LOGICAL_FILE_PREFIX, loId);
    var now = formattedDate(LocalDateTime.now());
    var file = new File(path);
    content.write(new FileWriter(file), "NTRIPLE");
    var fileSize = file.length();
    var queryParameters = ImmutableMap.<String, Object>builder()
                                      .put("graph", graph)
                                      .put("physicalFile", physicalFile)
                                      .put("logicalFile", logicalFile)
                                      .put("phyId", phyId)
                                      .put("phyFilename", phyFilename)
                                      .put("now", now)
                                      .put("fileSize", fileSize)
                                      .put("loId", loId)
                                      .put("logicalFileName", logicalFileName + ".nt")
                                      .put("fileExtension", "nt")
                                      .put("contentType", "application/n-triples").build();

    var queryStr = queryStore.getQueryWithParameters("writeTtlFile",queryParameters);
    sparqlClient.executeUpdateQuery(queryStr);
    return logicalFile;
  }

  public void appendTaskResultFile(Task task,
                                   DataContainer dataContainer) {
    var containerUri = dataContainer.getUri();
    var containerId = dataContainer.getId();
    var fileUri = dataContainer.getGraphUri();
    var queryParameters = Map.of(
            "containerUri", containerUri,
            "containerId", containerId,
            "fileUri", fileUri, "task", task
    );
    var queryStr = queryStore.getQueryWithParameters("appendTaskResultFile",queryParameters);

    sparqlClient.executeUpdateQuery(queryStr);

  }

  public void appendTaskResultGraph(Task task,
                                    DataContainer dataContainer) {
    var graphContainerUri = dataContainer.getUri();
    var graphContainerId = dataContainer.getId();
    var filteredGraph = dataContainer.getGraphUri();
    var queryParameters = Map.of(
            "graphContainerUri", graphContainerUri,
            "graphContainerId", graphContainerId,
            "filteredGraph", filteredGraph,
            "task", task
    );
    var queryStr = queryStore.getQueryWithParameters("appendTaskResultGraph", queryParameters);
    log.debug(queryStr);
    sparqlClient.executeUpdateQuery(queryStr);

  }

  public String selectInputContainerGraph(Task task) {
    String queryTask = queryStore.getQuery("selectInputContainerGraph").formatted(task.getTask());

    return sparqlClient.executeSelectQuery(queryTask, resultSet -> {
      if (!resultSet.hasNext()) {
        throw new RuntimeException("Input container graph not found");
      }
      var t = resultSet.next();
      return t.getResource("graph").getURI();
    });
  }

  public void appendTaskError(Task task, String message) {
    var id = uuid();
    var uri = ERROR_URI_PREFIX + id;

    Map<String, Object> parameters = Map.of("task", task, "id", id, "uri", uri, "message", message);
    var queryStr = queryStore.getQueryWithParameters("appendTaskError", parameters);

    sparqlClient.executeUpdateQuery(queryStr);
  }
}
