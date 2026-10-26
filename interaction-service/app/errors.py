from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse


class AppError(Exception):
    """Domain error rendered as {"code", "message"}, the same shape the Java backend returns."""

    status_code = status.HTTP_400_BAD_REQUEST
    code = "BAD_REQUEST"

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class UnauthorizedError(AppError):
    status_code = status.HTTP_401_UNAUTHORIZED
    code = "UNAUTHORIZED"


class ItemNotFoundError(AppError):
    status_code = status.HTTP_404_NOT_FOUND
    code = "ITEM_NOT_FOUND"


class ItemInactiveError(AppError):
    status_code = status.HTTP_409_CONFLICT
    code = "ITEM_INACTIVE"


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content={"code": exc.code, "message": exc.message})
