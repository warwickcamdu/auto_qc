[![DOI](https://zenodo.org/badge/169840753.svg)](https://zenodo.org/badge/latestdoi/169840753)


ImageJ plugin auto_qc

Created by group 4 of the NEUBIAS trainingschool 11.

Erick Ratamero, Cesar Augusto Valades Cruz, Christopher Schmied, Jan Eckhardt, Paul Stroe.


Continuing development by Erick Ratamero, experimental input from Claire Mitchell

https://www.warwick.ac.uk/camdu


Based on the .py scripts from Erick Ratamero:

https://github.com/erickmartins/autoQC_jython

==========================================================================================

Usage Instructions:

autoPSF: searches the input directory for images with the string "psf" on their names, expects a Z-stack.  
autoFOV: searches the input directory for images with the string "fov" on their names, expects a single 2d image.  
autoColoc: searches the input directory for images with the string "coloc" on their names, expects a 4d image (XYCZT).  
autoStageRepro: searches the input directory for images with the string "stage" on their names, expects a 3d image (timelapse).  



==========================================================================================
Citations:


Based on ImageJ:

Schneider, C. A.; Rasband, W. S. & Eliceiri, K. W. (2012), "NIH Image to ImageJ: 25 years of image analysis", Nature methods 9(7): 671-675, PMID 22930834

Used to run Fiji in the background:

Schindelin, J.; Arganda-Carreras, I. & Frise, E. et al. (2012), "Fiji: an open-source platform for biological-image analysis", Nature methods 9(7): 676-682, PMID 22743772, doi:10.1038/nmeth.2019


Uses the following plugins:

bioformats (https://www.openmicroscopy.org/bio-formats/)

Melissa Linkert, Curtis T. Rueden, Chris Allan, Jean-Marie Burel, Will Moore, Andrew Patterson, Brian Loranger, Josh Moore, Carlos Neves, Donald MacDonald, Aleksandra Tarkowska, Caitlin Sticco, Emma Hill, Mike Rossner, Kevin W. Eliceiri, and Jason R. Swedlow (2010) Metadata matters: access to image data in the real world. The Journal of Cell Biology 189(5), 777-782. doi: 10.1083/jcb.201004104


Uses code from:

MetroloJ (http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:metroloj:start)
Cédric Matthews and Fabrice P. Cordelieres, MetroloJ : an ImageJ plugin to help monitor microscopes' health, in ImageJ User & Developer Conference 2010 proceedings


==========================================================================================
Acknowledgements

Special thanks goes to:

- Robert Haase
- Jean-Yves Tinevez

For their help and guidance

NEUBIAS and NEUBIAS TS11 organisers for creating the environment

==========================================================================================


----------------------------------------------------------------------------------------
ToDo:

Documentation of the code

