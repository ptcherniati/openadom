``` mermaid
  classDiagram
    HashMap <|-- Internationalization
    InternationalizationApplicationMap *-- Internationalization:internationalization
    InternationalizationMap  "1"-->"*" InternationalizationApplicationMap
    InternationalizationMap  : + InternationalizationApplicationMap application
    InternationalizationMap  "*"-->"*" InternationalizationReferenceMap
    InternationalizationMap  : + Map<String, InternationalizationReferenceMap> references
    InternationalizationMap  "*"-->"*" InternationalizationDataTypeMap
    InternationalizationMap  : + Map<String, InternationalizationDataTypeMap> references
    InternationalizationDisplay
    InternationalizationReferenceMap "1"-->"*" Internationalization internationalizationName
    InternationalizationReferenceMap "1"-->"*" InternationalizationDisplay internationalizationDisplay
    InternationalizationReferenceMap  : + Map<String, InternationalizationDataTypeMap> internationalizationDisplay
```