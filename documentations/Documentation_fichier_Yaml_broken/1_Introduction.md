---
title: Documentation fichiers de configuration pour OpenADOM
subtitle: Documentation décrivant la structure du fichier de configuration de l'application OpenADOM
author:
  - TCHERNIATINSKY Philippe
  - VARLOTEAUX Lucile
date: \today
lang: fr-FR
numbersections: true
documentclass: scrreprt
output:
  pdf_document: 
    latex_engine: pdflatex
toc: true
toc-depth: 6
toc-title: "Table des matières"
fontsize: 12pt
mainfont: TeX Gyre Pagella
mainfontoptions:
- Numbers=Lowercase
- Numbers=Proportional
linestretch: 1
linkcolor: blue
colorlinks: true
urlstyle: sf
links-as-notes: true
link-citations: true
pagenumberinf: true
hyperrefoptions:
  - linktoc=all
  - pdfwindowui
hyphenation:
  csv: ;
usepackege: 
  hyphenate: true
---

# Introduction

Ce document permet d'aider un gestionnaire de Système d'Information (SI) à décrire son domaine dans un fichier de configuration. 
Lorsque l'on depose ce fichier dans l'application, cela une base de données. 
et les outils permettant de l'alimenter et de la consulter.

Chaque fichier de configuration déposé génèrera un schéma dédié dans la base de données.

## <a id="prealable" />Préalable
Un travail d'analyse de votre domaine est la premère chose à faire. Ce travail est le plus long et il aboutit en un découpage de votre domaine en type de données.

Chaque type de données correspondra à un format de fichier d'échange. Le format retenu est le format tabulaire [csv](#csv).
