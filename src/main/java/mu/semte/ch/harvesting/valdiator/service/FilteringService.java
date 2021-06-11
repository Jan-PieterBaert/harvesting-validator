package mu.semte.ch.harvesting.valdiator.service;

import lombok.extern.slf4j.Slf4j;
import mu.semte.ch.lib.dto.DataContainer;
import mu.semte.ch.lib.dto.Task;
import mu.semte.ch.lib.shacl.ShaclService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shacl.ValidationReport;
import org.springframework.stereotype.Service;

import static mu.semte.ch.harvesting.valdiator.Constants.FILTER_GRAPH_PREFIX;

@Service
@Slf4j
public class FilteringService {

  private final ShaclService shaclService;
  private final TaskService taskService;

  public FilteringService(ShaclService shaclService, TaskService taskService) {
    this.shaclService = shaclService;
    this.taskService = taskService;
  }

  public void runFilterPipeline(Task task) {
    var inputContainer = taskService.selectInputContainer(task).get(0);
    log.debug("input container: {}", inputContainer);
    var importedTriples = taskService.fetchTriplesFromInputContainer(inputContainer.getGraphUri());
    var fileContainer = DataContainer.builder().build();

    var report = taskService.fetchTriplesFromInputContainer(inputContainer.getValidationGraphUri());

    var validTriples = writeValidTriples(task, fileContainer, ShaclService.fromModel(report), importedTriples);

    writeErrorTriples(task, fileContainer, importedTriples, validTriples);

    // import filtered triples
    var filteredGraph = "%s/%s".formatted(FILTER_GRAPH_PREFIX, task.getId());

    taskService.importTriples(task, filteredGraph, validTriples);

    // append result graph
    var graphContainer = DataContainer.builder()
                                      .graphUri(filteredGraph)
                                      .build();
    taskService.appendTaskResultGraph(task, graphContainer);
  }

  private void writeErrorTriples(Task task, DataContainer fileContainer, Model importedTriples, Model validTriples) {
    var errorTriples = importedTriples.difference(validTriples);
    log.debug("Number of errored triples: {}", errorTriples.size());
    var dataContainer = fileContainer
            .toBuilder()
            .graphUri(taskService.writeTtlFile(task.getGraph(), errorTriples, "error-triples.ttl"))
            .build();
    taskService.appendTaskResultFile(task, dataContainer);
  }

  private Model writeValidTriples(Task task, DataContainer fileContainer, ValidationReport report, Model importedTriples) {
    log.debug("filter non conform triples...");
    var validTriples = shaclService.filter(importedTriples, report);
    var dataContainer = fileContainer.toBuilder()
                                     .graphUri(taskService.writeTtlFile(task.getGraph(), validTriples, "valid-triples.ttl"))
                                     .build();
    taskService.appendTaskResultFile(task, dataContainer);
    return validTriples;
  }



}
