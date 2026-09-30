
```mermaid

classDiagram

Map <|-- SequencedMap
Map  -->  Map.Entry

SequencedMap <|-- SortedMap

SortedMap <|-- NavigableMap
SortedMap "1" --> "1" Comparator

Map <|.. AbstractMap


AbstractMap <|-- TreeMap
NavigableMap <|.. TreeMap


AbstractMap <|-- HashMap
Map <|.. HashMap
Map.Entry <|.. HashMap.Node
LinkedHashMap.Entry <|-- HashMap.TreeNode

HashMap <|-- LinkedHashMap
SequencedMap <|.. LinkedHashMap
HashMap.Node <|-- LinkedHashMap.Entry

AbstractMap <|-- WeakHashMap
Map <|.. WeakHashMap
WeakHashMap "1" --> "1" ReferenceQueue



```