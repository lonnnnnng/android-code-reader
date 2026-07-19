<?php

final class UserController
{
    public function show(int $id): array
    {
        return ['id' => $id, 'active' => true];
    }
}
