# export JAVA_OPTS=-Xmx16G

all: compare-subsumptions-all classification-time-all abox-consistency-all

DATE := `date +%Y-%m-%d`

ontologies/nci-thesaurus.owl:
	cd ontologies && curl -L -O 'https://evs.nci.nih.gov/ftp1/NCI_Thesaurus/archive/23.11d_Release/Thesaurus.OWL.zip' && unzip Thesaurus.OWL.zip && mv Thesaurus.OWL nci-thesaurus.owl

ontologies/UNIV-BENCH-OWL2EL.owl:
	cd ontologies && curl -L -O 'https://github.com/kracr/owl2bench/raw/master/UNIV-BENCH-OWL2EL.owl'

ontologies/%.ofn: ontologies/%.owl
	robot convert -i $< -o $@.tmp.ofn && grep -v ObjectUnionOf $@.tmp.ofn | grep -v ObjectPropertyRange | grep -v ObjectHasSelf | grep -v ObjectInverse >$@

REASONERS = whelk elk-0.4.3 elk-0.6.0 #snorocket
ONTOLOGIES = nci-thesaurus uberon-go-cl-ro UNIV-BENCH-OWL2EL
PARALLELISM = 1 4 8
COMPARE_SUBSUMPTIONS_ALL = $(patsubst %,perf/compare-subsumptions-%.yaml,$(ONTOLOGIES))
MATRIX_CLASSIFY = $(foreach X,$(REASONERS),$(foreach Y,$(ONTOLOGIES),$X-$Y))
CLASSIFICATION_TIME_ALL = $(patsubst %,perf/classification-time-%.yaml,$(MATRIX_CLASSIFY))
MATRIX_PARALLELISM = $(foreach X,$(REASONERS),$(foreach Y,$(ONTOLOGIES),$(foreach Z,$(PARALLELISM),$X-$Y-$Z)))
DL_QUERY_SPEED_ALL = $(patsubst %,perf/dl-query-speed-%.yaml,$(MATRIX_PARALLELISM))
MATRIX_ABOX = $(foreach X,$(REASONERS),$(foreach Y,$(PARALLELISM),$X-$Y))
ABOX_CONSISTENCY_ALL = $(patsubst %,perf/abox-consistency-%.yaml,$(MATRIX_ABOX))

compare-subsumptions-all: $(COMPARE_SUBSUMPTIONS_ALL)

perf/compare-subsumptions-%.yaml: ontologies/%.ofn scala/classify-compare.sc
	mkdir -p perf && scala-cli run scala/classify-compare.sc -- $< >$@

classification-time-all: $(CLASSIFICATION_TIME_ALL)

dl-query-speed-all: $(DL_QUERY_SPEED_ALL)

abox-consistency-all: $(ABOX_CONSISTENCY_ALL)

.PHONY: compare-subsumptions-all classification-time-all

define CLASSIFICATION_SPEED_RULE
perf/classification-time-$(reasoner)-$(ontology).yaml: ontologies/$(ontology).ofn
	mkdir -p perf && scala-cli run scala/classify-speed-$(reasoner).sc -- ontologies/$(ontology).ofn >$$@
endef

$(foreach ontology,$(ONTOLOGIES), $(foreach reasoner,$(REASONERS), $(eval $(CLASSIFICATION_SPEED_RULE))))

define QUERY_SPEED_RULE
perf/dl-query-speed-$(reasoner)-$(ontology)-$(parallelism).yaml: ontologies/$(ontology).ofn
	mkdir -p perf && scala-cli run scala/dl-query-speed-$(reasoner).sc -- ontologies/$(ontology).ofn $(parallelism) >$$@
endef

$(foreach ontology,$(ONTOLOGIES), $(foreach reasoner,$(REASONERS), $(foreach parallelism,$(PARALLELISM), $(eval $(QUERY_SPEED_RULE)))))

define ABOX_CONSISTENCY_RULE
perf/abox-consistency-$(reasoner)-$(parallelism).yaml: ontologies/uberon-go-cl-ro.ofn
	mkdir -p perf && scala-cli run scala/abox-consistency-$(reasoner).sc -- ontologies/uberon-go-cl-ro.ofn go-cams $(parallelism) >$$@
endef

$(foreach reasoner,$(REASONERS), $(foreach parallelism,$(PARALLELISM), $(eval $(ABOX_CONSISTENCY_RULE))))
