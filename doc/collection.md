


```mermaid

classDiagram

Iterable <|-- Collection

Collection <|-- SequencedCollection

Collection <|-- Queue

Collection <|-- Set

SequencedCollection <|-- List

SequencedCollection <|-- Deque

SequencedCollection <|-- SequencedSet

Set <|-- SequencedSet

SequencedSet <|-- SortedSet

Set <|-- SortedSet

SortedSet <|-- NavigableSet

Queue <|-- Deque


Collection <|.. AbstractCollection

AbstractCollection <|-- AbstractList
List <|.. AbstractList

AbstractCollection <|-- AbstractSet
Set <|.. AbstractSet

AbstractCollection <|-- AbstractQueue
Queue <|.. AbstractQueue


AbstractList <|-- AbstractSequentialList

List <|.. ArrayList
AbstractList <|-- ArrayList

List <|.. LinkedList
Deque <|.. LinkedList
AbstractSequentialList <|-- LinkedList


AbstractSet <|-- TreeSet
NavigableSet <|.. TreeSet
TreeSet "1" --> "1" NavigableMap

AbstractSet <|-- HashSet
Set <|.. HashSet
HashSet "1" --> "1" LinkedHashMap

HashSet <|-- LinkedHashSet
SequencedSet <|.. LinkedHashSet
LinkedHashSet "1" --> "1" LinkedHashMap

AbstractQueue <|-- PriorityQueue
PriorityQueue "1" --> "1" Comparator
    
```
