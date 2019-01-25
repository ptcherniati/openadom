package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.ReferenceType;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.OreSiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class OreSiResources {

    @Autowired
    private OreSiRepository repo;

    @RequestMapping("/")
    public String home() {
        return "Hello World!";
    }


    @PostMapping(value="/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createFile( @RequestParam("data") MultipartFile file) throws IOException {
        if (!file.isEmpty()) {
            BinaryFile binaryFile = new BinaryFile();
            binaryFile.setName(file.getOriginalFilename());
            binaryFile.setSize(file.getSize());
            binaryFile.setData(file.getBytes());
            UUID result = repo.store(binaryFile);
            return ResponseEntity.created(URI.create("/files/" + result)).body(Map.of("id", result.toString()));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping(value = "/files/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable("id") UUID id) {
        Optional<BinaryFile> optionalBinaryFile = repo.findById(BinaryFile.class, id);
        if (optionalBinaryFile.isPresent()) {
            BinaryFile binaryFile = optionalBinaryFile.get();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentLength(binaryFile.getSize());
            headers.set("Content-disposition", "attachment;filename=" + binaryFile.getName());
            return new ResponseEntity(binaryFile.getData(), headers, HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value="/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createApplication(@RequestBody Application e) {
        UUID result = repo.store(e);
        return ResponseEntity.created(URI.create("/applications/" + result)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/applications/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Application> getApplication(@PathVariable("id") UUID id) {
        Optional<Application> opt = repo.findById(Application.class, id);
        return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value="/applications/{id}/references", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReference(@PathVariable("id") UUID id, @RequestParam("data") MultipartFile file) throws IOException {
        if (!file.isEmpty()) {
            BinaryFile binaryFile = new BinaryFile();
            binaryFile.setName(file.getOriginalFilename());
            binaryFile.setSize(file.getSize());
            binaryFile.setData(file.getBytes());
            UUID result = repo.store(binaryFile);

            return ResponseEntity.created(URI.create(String.format("/applications/%s/references/%s", id, result))).body(Map.of("id", result.toString()));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value="/referencetypes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReferenceType(@RequestBody ReferenceType e) {
        UUID result = repo.store(e);
        return ResponseEntity.created(URI.create("/referencetypes/" + result)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/referencetypes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReferenceType> getReferenceType(@PathVariable("id") UUID id) {
        Optional<ReferenceType> opt = repo.findById(ReferenceType.class, id);
        return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value="/referencevalue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReferenceValue(@RequestBody ReferenceValue e) {
        UUID result = repo.store(e);
        return ResponseEntity.created(URI.create("/referencevalue/" + result)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/referencevalue/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReferenceValue> getReferenceValue(@PathVariable("id") UUID id) {
        Optional<ReferenceValue> opt = repo.findById(ReferenceValue.class, id);
        return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }


}