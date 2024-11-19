package fr.inra.oresing.domain.exceptions.data.data;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class DeleteOnrepositoryApplicationNotAllowedException extends OreSiTechnicalException {
    private static final String DELETE_ON_REPOSITORY_APPLICATION_NOT_ALLOWED_EXCEPTION  ="DELETE_ON_REPOSITORY_APPLICATION_NOT_ALLOWED_EXCEPTION";
    public DeleteOnrepositoryApplicationNotAllowedException() {
        super(DELETE_ON_REPOSITORY_APPLICATION_NOT_ALLOWED_EXCEPTION);
    }
}